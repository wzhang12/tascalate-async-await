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
package net.tascalate.async.examples.bank;

import java.math.BigDecimal;
import java.util.concurrent.CompletionStage;

import net.tascalate.async.api.async;
import static net.tascalate.async.api.AsyncCall.*;

public class AccountTransactionService {

	@async public CompletionStage<BigDecimal> deposit(final BankAccount account, final BigDecimal amount) throws InterruptedException {
		Thread.sleep(2000L);
		account.amount = account.amount.add(amount);
		return asyncResult(account.amount);
	}
	
	@async public CompletionStage<BigDecimal> withdraw(final BankAccount account, final BigDecimal amount) throws InterruptedException, InsufficientFundsException {
		Thread.sleep(30L);
		if (amount.compareTo(account.amount) < 0) {
			account.amount = account.amount.subtract(amount);
			return asyncResult(account.amount);
		} else {
			throw new InsufficientFundsException();
		}
	}
	
}
