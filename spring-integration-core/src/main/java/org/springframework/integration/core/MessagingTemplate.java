/*
 * Copyright 2013 the original author or authors.
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
package org.springframework.integration.core;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.integration.support.channel.BeanFactoryChannelResolver;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.core.GenericMessagingTemplate;

/**
 * @author Gary Russell
 * @since 3.0
 *
 */
public class MessagingTemplate extends GenericMessagingTemplate {

	/**
	 * Overridden to set the destination resolver to a {@link BeanFactoryChannelResolver}.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		super.setDestinationResolver(new BeanFactoryChannelResolver(beanFactory));
	}

	/**
	 * Invokes {@code setDefaultDestination(MessageChannel)} - provided for
	 * backward compatibility.
	 * @param channel the channel to set.
	 */
	public void setDefaultChannel(MessageChannel channel) {
		super.setDefaultDestination(channel);
	}

}
