/*
 * Copyright 2012 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.wasync.util;

import com.ning.http.client.AsyncHttpClient;
import org.atmosphere.wasync.Client;
import org.atmosphere.wasync.ClientFactory;
import org.atmosphere.wasync.Function;
import org.atmosphere.wasync.Options;
import org.atmosphere.wasync.Request;
import org.atmosphere.wasync.RequestBuilder;
import org.atmosphere.wasync.Socket;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A utility class that can be used to load a WebSocket enabled Server
 *
 * @author jeanfrancois Arcand
 */
public class WebSocketLoadTest {

    public static void main(String[] s) throws InterruptedException, IOException {

        final int clientNum = Integer.valueOf(s[0]);
        final int messageNum = Integer.valueOf(s[1]);
        String url = s[2];

        System.out.println("Number of Client: " + clientNum);
        System.out.println("Number of Message: " + messageNum);

        final AsyncHttpClient c = new AsyncHttpClient();

        final CountDownLatch l = new CountDownLatch(clientNum);

        final CountDownLatch messages = new CountDownLatch(messageNum);

        Client client = ClientFactory.getDefault().newClient();
        RequestBuilder request = client.newRequestBuilder();
        request.method(Request.METHOD.GET).uri(url);
        request.transport(Request.TRANSPORT.WEBSOCKET);

        long clientCount = l.getCount();
        final AtomicLong total = new AtomicLong(0);

        for (int i = 0; i < clientCount; i++) {
            final AtomicLong start = new AtomicLong(0);
            Socket socket = client.create(new Options.OptionsBuilder().runtime(c).build());
            socket.on(new Function<Integer>() {
                @Override
                public void on(Integer statusCode) {
                    start.set(System.currentTimeMillis());
                    l.countDown();
                }
            });
            socket.on(new Function<String>() {

                int mCount = 0;

                @Override
                public void on(String s) {
                    if (s.startsWith("message")) {
                        if (++mCount == messageNum) {
                            total.addAndGet(System.currentTimeMillis() - start.get());
                            messages.countDown();
                        }
                    }
                }
            }).on(new Function<Throwable>() {
                @Override
                public void on(Throwable t) {
                    t.printStackTrace();
                }
            });

            socket.open(request.build());
        }
        l.await(60, TimeUnit.SECONDS);

        System.out.println("OK, all Connected: " + clientNum);

        long count = messageNum;
        Socket socket = client.create(new Options.OptionsBuilder().runtime(c).build());
        socket.open(request.build());
        for (int i = 0; i < count; i++ ) {
            socket.fire("message" + i);
        }
        messages.await(5, TimeUnit.MINUTES);
        c.close();
        System.out.println("Total: " + (total.get()/clientCount));

    }

}
