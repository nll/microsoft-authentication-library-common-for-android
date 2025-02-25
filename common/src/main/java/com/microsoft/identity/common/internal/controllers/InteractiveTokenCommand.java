//  Copyright (c) Microsoft Corporation.
//  All rights reserved.
//
//  This code is licensed under the MIT License.
//
//  Permission is hereby granted, free of charge, to any person obtaining a copy
//  of this software and associated documentation files(the "Software"), to deal
//  in the Software without restriction, including without limitation the rights
//  to use, copy, modify, merge, publish, distribute, sublicense, and / or sell
//  copies of the Software, and to permit persons to whom the Software is
//  furnished to do so, subject to the following conditions :
//
//  The above copyright notice and this permission notice shall be included in
//  all copies or substantial portions of the Software.
//
//  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
//  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
//  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
//  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
//  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
//  THE SOFTWARE.
package com.microsoft.identity.common.internal.controllers;

import android.content.Intent;

import com.microsoft.identity.common.exception.BaseException;
import com.microsoft.identity.common.internal.logging.Logger;
import com.microsoft.identity.common.internal.request.AcquireTokenOperationParameters;
import com.microsoft.identity.common.internal.request.ILocalAuthenticationCallback;
import com.microsoft.identity.common.internal.result.AcquireTokenResult;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class InteractiveTokenCommand extends TokenCommand {

    private static final String TAG = InteractiveTokenCommand.class.getSimpleName();

    public InteractiveTokenCommand(AcquireTokenOperationParameters parameters,
                                   BaseController controller,
                                   CommandCallback callback) {

        super(parameters, controller, callback);
    }

    @Override
    public AcquireTokenResult execute() throws InterruptedException, ExecutionException, IOException, BaseException {
        final String methodName = ":execute";
        if (getParameters() instanceof AcquireTokenOperationParameters) {
            Logger.info(
                    TAG + methodName,
                    "Executing interactive token command..."
            );

            return getDefaultController()
                    .acquireToken(
                            (AcquireTokenOperationParameters) getParameters()
                    );
        } else {
            throw new IllegalArgumentException("Invalid operation parameters");
        }
    }

    @Override
    public void notify(int requestCode, int resultCode, final Intent data) {
        getDefaultController().completeAcquireToken(requestCode, resultCode, data);
    }
}
