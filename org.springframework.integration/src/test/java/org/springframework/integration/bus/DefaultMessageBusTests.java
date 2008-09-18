/*
 * Copyright 2002-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.bus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.integration.channel.ChannelRegistry;
import org.springframework.integration.channel.PollableChannel;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.endpoint.AbstractInOutEndpoint;
import org.springframework.integration.endpoint.SourcePollingChannelAdapter;
import org.springframework.integration.message.ErrorMessage;
import org.springframework.integration.message.GenericMessage;
import org.springframework.integration.message.Message;
import org.springframework.integration.message.MessageBuilder;
import org.springframework.integration.message.PollableSource;
import org.springframework.integration.message.StringMessage;
import org.springframework.integration.scheduling.PollingSchedule;

/**
 * @author Mark Fisher
 */
public class DefaultMessageBusTests {

	@Test
	public void testRegistrationWithInputChannelReference() {
		GenericApplicationContext context = new GenericApplicationContext();
		QueueChannel sourceChannel = new QueueChannel();
		QueueChannel targetChannel = new QueueChannel();
		sourceChannel.setBeanName("sourceChannel");
		targetChannel.setBeanName("targetChannel");
		context.getBeanFactory().registerSingleton("sourceChannel", sourceChannel);
		context.getBeanFactory().registerSingleton("targetChannel", targetChannel);
		Message<String> message = MessageBuilder.withPayload("test")
				.setReturnAddress("targetChannel").build();
		sourceChannel.send(message);
		AbstractInOutEndpoint endpoint = new AbstractInOutEndpoint() {
			public Message<?> handle(Message<?> message) {
				return message;
			}
		};
		endpoint.setBeanName("testEndpoint");
		endpoint.setInputChannel(sourceChannel);
		context.getBeanFactory().registerSingleton("testEndpoint", endpoint);
		context.refresh();
		DefaultMessageBus bus = new DefaultMessageBus();
		bus.setApplicationContext(context);
		bus.start();
		Message<?> result = targetChannel.receive(3000);
		assertEquals("test", result.getPayload());
		bus.stop();
	}

	@Test
	public void testChannelsWithoutHandlers() {
		GenericApplicationContext context = new GenericApplicationContext();
		DefaultMessageBus bus = new DefaultMessageBus();
		bus.setApplicationContext(context);
		QueueChannel sourceChannel = new QueueChannel();
		sourceChannel.setBeanName("sourceChannel");
		context.getBeanFactory().registerSingleton("sourceChannel", sourceChannel);
		sourceChannel.send(new StringMessage("test"));
		QueueChannel targetChannel = new QueueChannel();
		targetChannel.setBeanName("targetChannel");
		context.getBeanFactory().registerSingleton("targetChannel", targetChannel);
		bus.start();
		Message<?> result = targetChannel.receive(100);
		assertNull(result);
		bus.stop();
	}

	@Test
	public void testAutodetectionWithApplicationContext() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("messageBusTests.xml", this.getClass());
		context.start();
		PollableChannel sourceChannel = (PollableChannel) context.getBean("sourceChannel");
		sourceChannel.send(new GenericMessage<String>("test"));		
		PollableChannel targetChannel = (PollableChannel) context.getBean("targetChannel");
		MessageBus bus = (MessageBus) context.getBean("bus");
		bus.start();
		Message<?> result = targetChannel.receive(1000);
		assertEquals("test", result.getPayload());
	}

	@Test
	public void testExactlyOneHandlerReceivesPointToPointMessage() {
		GenericApplicationContext context = new GenericApplicationContext();
		QueueChannel inputChannel = new QueueChannel();
		QueueChannel outputChannel1 = new QueueChannel();
		QueueChannel outputChannel2 = new QueueChannel();
		AbstractInOutEndpoint endpoint1 = new AbstractInOutEndpoint() {
			public Message<?> handle(Message<?> message) {
				return MessageBuilder.fromMessage(message).build();
			}
		};
		AbstractInOutEndpoint endpoint2 = new AbstractInOutEndpoint() {
			public Message<?> handle(Message<?> message) {
				return MessageBuilder.fromMessage(message).build();
			}
		};
		inputChannel.setBeanName("input");
		outputChannel1.setBeanName("output1");
		outputChannel2.setBeanName("output2");
		context.getBeanFactory().registerSingleton("input", inputChannel);
		context.getBeanFactory().registerSingleton("output1", outputChannel1);
		context.getBeanFactory().registerSingleton("output2", outputChannel2);
		endpoint1.setBeanName("testEndpoint1");
		endpoint1.setInputChannel(inputChannel);
		endpoint1.setOutputChannel(outputChannel1);
		endpoint2.setBeanName("testEndpoint2");
		endpoint2.setInputChannel(inputChannel);
		endpoint2.setOutputChannel(outputChannel2);
		context.getBeanFactory().registerSingleton("testEndpoint1", endpoint1);
		context.getBeanFactory().registerSingleton("testEndpoint2", endpoint2);
		DefaultMessageBus bus = new DefaultMessageBus();
		bus.setApplicationContext(context);
		bus.start();
		inputChannel.send(new StringMessage("testing"));
		Message<?> message1 = outputChannel1.receive(500);
		Message<?> message2 = outputChannel2.receive(0);
		bus.stop();
		assertTrue("exactly one message should be null", message1 == null ^ message2 == null);
	}

	@Test
	public void testBothHandlersReceivePublishSubscribeMessage() throws InterruptedException {
		GenericApplicationContext context = new GenericApplicationContext();
		PublishSubscribeChannel inputChannel = new PublishSubscribeChannel();
		QueueChannel outputChannel1 = new QueueChannel();
		QueueChannel outputChannel2 = new QueueChannel();
		final CountDownLatch latch = new CountDownLatch(2);
		AbstractInOutEndpoint endpoint1 = new AbstractInOutEndpoint() {
			public Message<?> handle(Message<?> message) {
				Message<?> reply = MessageBuilder.fromMessage(message).build();
				latch.countDown();
				return reply;
			}
		};
		AbstractInOutEndpoint endpoint2 = new AbstractInOutEndpoint() {
			public Message<?> handle(Message<?> message) {
				Message<?> reply = MessageBuilder.fromMessage(message).build();
				latch.countDown();
				return reply;
			}
		};
		inputChannel.setBeanName("input");
		outputChannel1.setBeanName("output1");
		outputChannel2.setBeanName("output2");
		context.getBeanFactory().registerSingleton("input", inputChannel);
		context.getBeanFactory().registerSingleton("output1", outputChannel1);
		context.getBeanFactory().registerSingleton("output2", outputChannel2);
		endpoint1.setBeanName("testEndpoint1");
		endpoint1.setInputChannel(inputChannel);
		endpoint1.setOutputChannel(outputChannel1);
		endpoint2.setBeanName("testEndpoint2");
		endpoint2.setInputChannel(inputChannel);
		endpoint2.setOutputChannel(outputChannel2);
		context.getBeanFactory().registerSingleton("testEndpoint1", endpoint1);
		context.getBeanFactory().registerSingleton("testEndpoint2", endpoint2);
		DefaultMessageBus bus = new DefaultMessageBus();
		bus.setApplicationContext(context);
		bus.start();
		inputChannel.send(new StringMessage("testing"));
		latch.await(500, TimeUnit.MILLISECONDS);
		assertEquals("both handlers should have been invoked", 0, latch.getCount());
		Message<?> message1 = outputChannel1.receive(500);
		Message<?> message2 = outputChannel2.receive(500);
		bus.stop();
		assertNotNull("both handlers should have replied to the message", message1);
		assertNotNull("both handlers should have replied to the message", message2);
	}

	@Test
	public void testErrorChannelWithFailedDispatch() throws InterruptedException {
		GenericApplicationContext context = new GenericApplicationContext();
		QueueChannel errorChannel = new QueueChannel();
		QueueChannel outputChannel = new QueueChannel();
		errorChannel.setBeanName("errorChannel");
		context.getBeanFactory().registerSingleton("errorChannel", errorChannel);
		CountDownLatch latch = new CountDownLatch(1);
		SourcePollingChannelAdapter channelAdapter = new SourcePollingChannelAdapter();
		channelAdapter.setSource(new FailingSource(latch));
		channelAdapter.setSchedule(new PollingSchedule(1000));
		channelAdapter.setOutputChannel(outputChannel);
		channelAdapter.setBeanName("testChannel");
		context.getBeanFactory().registerSingleton("testChannel", channelAdapter);
		DefaultMessageBus bus = new DefaultMessageBus();
		bus.setApplicationContext(context);
		bus.start();
		latch.await(2000, TimeUnit.MILLISECONDS);
		Message<?> message = errorChannel.receive(5000);
		bus.stop();
		assertNull(outputChannel.receive(0));
		assertNotNull("message should not be null", message);
		assertTrue(message instanceof ErrorMessage);
		Throwable exception = ((ErrorMessage) message).getPayload();
		assertEquals("intentional test failure", exception.getMessage());
	}

	@Test(expected = BeanCreationException.class)
	public void testMultipleMessageBusBeans() {
		new ClassPathXmlApplicationContext("multipleMessageBusBeans.xml", this.getClass());
	}

	@Test
	public void testErrorChannelRegistration() {
		DefaultMessageBus bus = new DefaultMessageBus();
		QueueChannel errorChannel = new QueueChannel();
		errorChannel.setBeanName(ChannelRegistry.ERROR_CHANNEL_NAME);
		bus.registerChannel(errorChannel);
		assertEquals(errorChannel, bus.getErrorChannel());
	}

	@Test
	public void testHandlerSubscribedToErrorChannel() throws InterruptedException {
		GenericApplicationContext context = new GenericApplicationContext();
		QueueChannel errorChannel = new QueueChannel();
		errorChannel.setBeanName(ChannelRegistry.ERROR_CHANNEL_NAME);
		context.getBeanFactory().registerSingleton(ChannelRegistry.ERROR_CHANNEL_NAME, errorChannel);
		final CountDownLatch latch = new CountDownLatch(1);
		AbstractInOutEndpoint endpoint = new AbstractInOutEndpoint() {
			public Message<?> handle(Message<?> message) {
				latch.countDown();
				return null;
			}
		};
		endpoint.setBeanName("testEndpoint");
		endpoint.setInputChannel(errorChannel);
		context.getBeanFactory().registerSingleton("testEndpoint", endpoint);
		DefaultMessageBus bus = new DefaultMessageBus();
		bus.setApplicationContext(context);
		bus.start();
		errorChannel.send(new ErrorMessage(new RuntimeException("test-exception")));
		latch.await(1000, TimeUnit.MILLISECONDS);
		assertEquals("handler should have received error message", 0, latch.getCount());
	}

	@Test
	public void testMessageBusAwareImpl() {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("messageBusTests.xml", this.getClass());
		TestMessageBusAwareImpl messageBusAwareBean = (TestMessageBusAwareImpl) context.getBean("messageBusAwareBean");
		assertTrue(messageBusAwareBean.getMessageBus() == context.getBean("bus"));
	}


	private static class FailingSource implements PollableSource<Object> {

		private CountDownLatch latch;

		public FailingSource(CountDownLatch latch) {
			this.latch = latch;
		}

		public Message<Object> receive() {
			latch.countDown();
			throw new RuntimeException("intentional test failure");
		}
	}

}
