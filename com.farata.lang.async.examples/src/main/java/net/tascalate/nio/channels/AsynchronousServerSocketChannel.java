package net.tascalate.nio.channels;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.CompletionHandler;
import java.util.Set;

import net.tascalate.concurrent.CompletionFuture;

public class AsynchronousServerSocketChannel extends java.nio.channels.AsynchronousServerSocketChannel {

    final protected java.nio.channels.AsynchronousServerSocketChannel delegate;
    
    public static AsynchronousServerSocketChannel open() throws IOException {
        return new AsynchronousServerSocketChannel(java.nio.channels.AsynchronousServerSocketChannel.open());
    }
    
    public static AsynchronousServerSocketChannel open(AsynchronousChannelGroup group) throws IOException {
        return new AsynchronousServerSocketChannel(java.nio.channels.AsynchronousServerSocketChannel.open(group));
    }
    
    protected AsynchronousServerSocketChannel(java.nio.channels.AsynchronousServerSocketChannel delegate) {
        super(delegate.provider());
        this.delegate = delegate;
    }

    public boolean isOpen() {
        return delegate.isOpen();
    }

    public void close() throws IOException {
        delegate.close();
    }

    public <T> T getOption(SocketOption<T> name) throws IOException {
        return delegate.getOption(name);
    }

    public Set<SocketOption<?>> supportedOptions() {
        return delegate.supportedOptions();
    }

    public AsynchronousServerSocketChannel bind(SocketAddress local, int backlog) throws IOException {
        delegate.bind(local, backlog);
        return this;
    }

    public <T> AsynchronousServerSocketChannel setOption(SocketOption<T> name, T value) throws IOException {
        delegate.setOption(name, value);
        return this;
    }
    
    public CompletionFuture<AsynchronousSocketChannel> acceptClient() {
        return doAccept(null, null);
    }

    public CompletionFuture<java.nio.channels.AsynchronousSocketChannel> accept() {
        return doAccept(null, null);
    }
    
    public <A> void acceptClient(A attachment, CompletionHandler<AsynchronousSocketChannel, ? super A> handler) {
        doAccept(attachment, handler);
    }
    
    public <A> void accept(A attachment, CompletionHandler<java.nio.channels.AsynchronousSocketChannel, ? super A> handler) {
        doAccept(attachment, handler);
    }

    public SocketAddress getLocalAddress() throws IOException {
        return delegate.getLocalAddress();
    }
    
    protected <V extends java.nio.channels.AsynchronousSocketChannel, A> CompletionFuture<V> doAccept(A attachment, CompletionHandler<V, ? super A> handler) {
        final AsyncResult<V, ? super A> asyncResult = new AsyncResult<>(handler);
        delegate.accept(attachment, new CompletionHandler<java.nio.channels.AsynchronousSocketChannel, A>() {

            @Override
            public void completed(java.nio.channels.AsynchronousSocketChannel result, A attachment) {
                @SuppressWarnings("unchecked")
                final V wrapped = (V)new AsynchronousSocketChannel(result);
                asyncResult.handler.completed(wrapped, attachment);
            }

            @Override
            public void failed(Throwable exc, A attachment) {
                asyncResult.handler.failed(exc, attachment);
            }
            
        });
        return asyncResult;
    }

}
