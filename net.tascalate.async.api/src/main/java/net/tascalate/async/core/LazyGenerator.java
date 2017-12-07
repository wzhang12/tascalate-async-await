/**
 * ﻿Copyright 2015-2017 Valery Silaev (http://vsilaev.com)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:

 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.

 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.

 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.tascalate.async.core;

import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.apache.commons.javaflow.api.continuable;

import net.tascalate.async.api.Generator;
import net.tascalate.concurrent.Promises;

class LazyGenerator<T> implements Generator<T> {

    private CompletableFuture<?> consumerLock;
    private CompletableFuture<?> producerLock;
    private Throwable lastError = null;
    private boolean done = false;

    private State<T> currentState = emptyState();
    private Object producerParam = NOTHING;
    private T latestResult = null;

    LazyGenerator() {
        producerLock = new CompletableFuture<>();
    }

    @Override
    public boolean next() {
        return next(NOTHING);
    }

    @Override
    public boolean next(Object producerParam) {
        // If we have synchronous error in generator method
        // (as opposed to asynchronous that is managed by consumerLock
        if (null != lastError) {
            Throwable error = lastError;
            lastError = null;
            Either.sneakyThrow(error);
        }
        // Could we advance further current state?
        if (currentState.advance()) {
            // Should be checked before done to let iterate over 
            // chained generators fully
            latestResult = currentState.currentValue();
            return true;
        }
        
        if (done) {
            return false;
        }

        this.producerParam = producerParam;        
        // Let produce some value (resumes producer)
        releaseProducerLock();
        // Wait till value is ready (suspends consumer)
        acquireConsumerLock();
        consumerLock = new CompletableFuture<>();
        // Check everything once again after wait
        return next(producerParam);
    }

    @Override
    public T current() {
        return currentState.currentValue();
    }

    @Override
    public void close() {
        currentState.close();
        if (null != producerLock) {
            final CompletableFuture<?> lock = producerLock;
            producerLock = null;
            lock.completeExceptionally(CloseSignal.INSTANCE);
        }
        end(null);
    }

    @continuable
    Object produce(T readyValue) {
        return produce(new ReadyValueState<>(readyValue));
    }

    @continuable
    Object produce(CompletionStage<T> pendingValue) {
        return produce(new PendingValueState<>(pendingValue));
    }

    @continuable
    Object produce(Generator<T> values) {
        return produce(new ChainedGeneratorState<T>(values));
    }

    private @continuable Object produce(State<T> state) {
        // Get and re-set producerLock
        acquireProducerLock();
        producerLock = new CompletableFuture<>();
        currentState = state;
        releaseConsumerLock();
        // return producerParam; // To have a semi-lazy generator that forwards till next yield
        return acquireProducerLock();
    }

    @continuable
    void begin() {
        acquireProducerLock();
    }

    void end(Throwable ex) {
        done = true;
        currentState = emptyState();
        // Set synchronous error in generator method
        // (as opposed to asynchronous that is managed by consumerLock        
        lastError = ex;
        releaseConsumerLock();
    }

    @continuable
    Object acquireProducerLock() {
        if (null == producerLock || producerLock.isDone()) {
            return producerFeedback();
        }
        // Order matters - set to null only after wait
        AsyncMethodExecutor.await(producerLock);
        producerLock = null;
        return producerFeedback();
    }

    private void releaseProducerLock() {
        if (null != producerLock) {
            final CompletableFuture<?> lock = producerLock;
            producerLock = null;
            lock.complete(null);
        }
    }

    private @continuable void acquireConsumerLock() {
        if (null == consumerLock || consumerLock.isDone()) {
            return;
        }
        // Order matters - set to null only after wait        
        AsyncMethodExecutor.await(consumerLock);
        consumerLock = null;
    }

    private void releaseConsumerLock() {
        if (null != consumerLock) {
            final CompletableFuture<?> lock = consumerLock;
            consumerLock = null;
            lock.complete(null);
        }
    }

    private Object producerFeedback() {
        if (NOTHING == producerParam) {
            return latestResult;
        } else {
            return producerParam;
        }        
    }
    
    abstract static class State<T> {
        abstract @continuable boolean advance();
        abstract T currentValue();
        abstract void close();
    }

    static class ReadyValueState<T> extends State<T> {
        final private T readyValue;
        private boolean iterated = false;

        public ReadyValueState(T readyValue) {
            this.readyValue = readyValue;
        }
        
        @Override boolean advance() {
            if (iterated) {
                return false;
            }
            iterated = true;
            return true;
        }

        @Override T currentValue() {
            if (!iterated) {
                throw new IllegalStateException();
            }
            return readyValue;
        }
        
        @Override void close() {
            iterated = true;
        }
    }

    static class PendingValueState<T> extends State<T> {
        private CompletionStage<T> pendingValue;
        private T readyValue;
        
        public PendingValueState(CompletionStage<T> pendingValue) {
            this.pendingValue = pendingValue;
        }

        @Override boolean advance() {
            if (null == pendingValue) {
                return false;
            }
            readyValue = AsyncMethodExecutor.await(pendingValue);
            pendingValue = null;
            return true;
        }
        
        @Override T currentValue() {
            if (null != pendingValue) {
                throw new IllegalStateException();
            }
            return readyValue;
        }
        
        @Override void close() {
            if (null != pendingValue) {
                // Need to double-check: probably this state is illegal
                // Only consumer may initiate close and only consumer awaits 
                // on the pending value.
                // So this may be an error when we are closing non-awaited value
                Promises.from(pendingValue).cancel(true);
                pendingValue = null;
            }
        }
    }
    
    static class ChainedGeneratorState<T> extends State<T> {
        final private Generator<T> source;

        public ChainedGeneratorState(Generator<T> source) {
            this.source = source;
        }

        @Override boolean advance() {
            return source.next();
        }
        
        @Override T currentValue() {
            return source.current();
        }
        
        @Override void close() {
            source.close();
        }
    }
    
    @SuppressWarnings("unchecked")
    private static <T> State<T> emptyState() {
        return (State<T>) EMPTY_STATE;
    }
    
    private final static State<?> EMPTY_STATE = new State<Object>() {
        
        @Override
        Object currentValue() {
            throw new NoSuchElementException();
        }
        
        @Override
        void close() {
        }
        
        @Override
        boolean advance() {
            return false;
        }
    };


    private static final Object NOTHING = new byte[0];
}
