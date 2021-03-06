/**
 * Mule Amazon SQS Connector
 *
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.modules.sqs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.*;

import org.apache.commons.lang.StringUtils;
import org.mule.api.ConnectionException;
import org.mule.api.ConnectionExceptionCode;
import org.mule.api.annotations.*;
import org.mule.api.annotations.display.FriendlyName;
import org.mule.api.annotations.display.Placement;
import org.mule.api.annotations.param.ConnectionKey;
import org.mule.api.annotations.param.Default;
import org.mule.api.annotations.param.Optional;
import org.mule.api.callback.SourceCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Amazon Simple Queue Service (Amazon SQS) is a distributed queue messaging service introduced by Amazon.com in
 * April of 2006. It supports programmatic sending of messages via web service applications as a way to communicate
 * over the internet. The intent of SQS is to provide a highly scalable hosted message queue that resolves issues
 * arising from the common producer-consumer problem or connectivity between producer and consumer.
 * <p/>
 * This connector does not provide a method for creating a queue. The reason being that it will automatically
 * create it when its needed instead of having to manually specify so.
 *
 * @author MuleSoft, Inc.
 */
@Connector(name = "sqs", friendlyName = "Amazon SQS", minMuleVersion = "3.5")
public class SQSConnector {
    private static Logger logger = LoggerFactory.getLogger(SQSConnector.class);

    /**
     * Message Queue
     */
    private AmazonSQSClient msgQueue;

    /**
     * Queue Region
     */
    @Optional
    @Configurable
    @Placement(group = "Optional Parameters") @FriendlyName("Region Endpoint")
    private RegionEndpoint region;
    
    /**
     * The queue URL to connect to. It takes priority over the queue name defined
     * in the connection parameters.
     */
    @Optional
    @Configurable
    @Placement(group = "Optional Parameters") @FriendlyName("Queue URL")
    private String queueUrl;

	private String accessKey;


    /**
     * @param accessKey AWS access key
     * @param secretKey AWS secret key
     * @param queueName The name of the queue to connect to (optional)
     * @throws ConnectionException If a connection cannot be made
     */
    @Connect
    public void connect(@ConnectionKey String accessKey, String secretKey, @Optional String queueName)
             throws ConnectionException {
        try {
            msgQueue = new AmazonSQSClient(new BasicAWSCredentials(accessKey, secretKey));

            if(region != null) {
                msgQueue.setEndpoint(region.value());
            }
            if (queueName != null) {
	            CreateQueueRequest createQueueRequest = new CreateQueueRequest(queueName);
	            setQueueUrl(msgQueue.createQueue(createQueueRequest).getQueueUrl());
            } else if (queueUrl != null) {
            	setQueueUrl(queueUrl);
            } else {
            	throw new ConnectionException(ConnectionExceptionCode.INCORRECT_CREDENTIALS, null, "A queue name or queue URL must be specified to make a connection.");
            }
        } catch (Exception e) {
            throw new ConnectionException(ConnectionExceptionCode.UNKNOWN, null, e.getMessage(), e);
        }
        setAccessKey(accessKey);
    }

    public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}
    
    @ConnectionIdentifier
    public String getAccessKey() {
    	return this.accessKey;
    }

	@Disconnect
    public void disconnect() {
        msgQueue = null;
    }
    
    @ValidateConnection
    public boolean isConnected() {
        return this.msgQueue != null;
    }

    /**
     * Sends a message to a specified queue. The message must be between 1 and 256K bytes long.
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:send-message}
     *
     * @param message the message to send. Defaults to the payload of the Mule message.
     * @param queueUrl the queue where the message is to be sent.
     * @return The queue response of the sent message.
     * @throws AmazonClientException
     *             If any internal errors are encountered inside the client while
     *             attempting to make the request or handle the response.  For example
     *             if a network connection is not available.
     * @throws AmazonServiceException
     *             If an error response is returned by AmazonSQS indicating
     *             either a problem with the data in the request, or a server side issue.
     */
    @Processor
    @InvalidateConnectionOn(exception = AmazonClientException.class)
    public SendMessageResult sendMessage(@Optional @Default("#[payload]") final String message, @Optional String queueUrl)
            throws AmazonServiceException {
    	return msgQueue.sendMessage(new SendMessageRequest(getQueueUrl(queueUrl), message));
    }

    /**
     * This method provides the URL for the message queue represented by this object.
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:get-url}
     *
     * @return generated queue service url
     */
    @Processor
    public String getUrl() {
        return this.queueUrl;
    }
    

    /**
     * Attempts to receive a message from the queue. Every attribute of the incoming
     * message will be added as an inbound property. Also the following properties
     * will also be added:
     * <p/>
     * sqs.message.id = containing the message identification
     * sqs.message.receipt.handle = containing the message identification
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:receive-messages}
     *
     * @param callback          Callback to call when a new message is available.
     * @param visibilityTimeout the duration (in seconds) the retrieved message is hidden from
     *                          subsequent calls to retrieve.
     * @param preserveMessages 	Flag that indicates if you want to preserve the messages
     *                         	in the queue. False by default, so the messages are
     *                         	going to be deleted.
     * @param pollPeriod        time in milliseconds to wait between polls (when no messages were retrieved). 
     *                          Default period is 1000 ms.
     * @param numberOfMessages  the number of messages to be retrieved on each call (10 messages max). 
     * 							By default, 1 message will be retrieved.	
     * @param queueUrl the queue URL where messages are to be fetched from.		                        
     * @throws AmazonClientException
     *             If any internal errors are encountered inside the client while
     *             attempting to make the request or handle the response.  For example
     *             if a network connection is not available.
     * @throws AmazonServiceException
     *             If an error response is returned by AmazonSQS indicating
     *             either a problem with the data in the request, or a server side issue.
     */
    @Source
    @InvalidateConnectionOn(exception = AmazonClientException.class)
    public void receiveMessages(SourceCallback callback, 
                                @Optional @Default("30") Integer visibilityTimeout, 
                                @Optional @Default("false") Boolean preserveMessages,
                                @Optional @Default("1000") Long pollPeriod,
                                @Optional @Default("1") Integer numberOfMessages,
                                @Optional String queueUrl)
            throws AmazonServiceException {

        List<Message> messages;
        ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
        receiveMessageRequest.setQueueUrl(getQueueUrl(queueUrl));

        if (visibilityTimeout != null) {
            receiveMessageRequest.setVisibilityTimeout(visibilityTimeout);
        }
        receiveMessageRequest.setMaxNumberOfMessages(numberOfMessages);
        
        while (!Thread.interrupted()) {
            messages = msgQueue.receiveMessage(receiveMessageRequest).getMessages();
            try
            {
                if (messages.size() == 0) {
                	Thread.sleep(pollPeriod);
                    continue;
                }
                for (Message msg : messages) {
                	callback.process(msg.getBody(), createProperties(msg));
                	if (!preserveMessages) {
                		msgQueue.deleteMessage(new DeleteMessageRequest(getQueueUrl(queueUrl), msg.getReceiptHandle()));
                	}
                }
            }
            catch (InterruptedException e)
            {
            	logger.error(e.getMessage(), e);
            }
            catch (Exception e)
            {
                throw new AmazonClientException("Error while processing message.", e);
            }
        }
    }

    /**
     * @param msg Message to extract properties from.
     * @return map with properties
     */
    public Map<String, Object> createProperties(Message msg)
    {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.putAll(msg.getAttributes());
        properties.put("sqs.message.id", msg.getMessageId());
        properties.put("sqs.message.receipt.handle", msg.getReceiptHandle());
        return properties;
    }

    /**
     * Deletes the message identified by message object on the queue this object represents.
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:delete-message}
     *
     * @param receiptHandle Receipt handle of the message to be deleted
     * @param queueUrl The URL of the queue to delete messages from
     * @throws AmazonClientException
     *             If any internal errors are encountered inside the client while
     *             attempting to make the request or handle the response.  For example
     *             if a network connection is not available.
     * @throws AmazonServiceException
     *             If an error response is returned by AmazonSQS indicating
     *             either a problem with the data in the request, or a server side issue.
     */
    @Processor
    @InvalidateConnectionOn(exception = AmazonClientException.class)
    public void deleteMessage(@Optional @Default("#[header:inbound:sqs.message.receipt.handle]") String receiptHandle,
    		@Optional String queueUrl)
            throws AmazonServiceException {
        msgQueue.deleteMessage(new DeleteMessageRequest(getQueueUrl(queueUrl), receiptHandle));
    }

    /**
     * Deletes the message queue represented by this object. Will delete non-empty queue.
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:delete-queue}
     * @param queueUrl The URL of the queue to delete.
     * @throws AmazonClientException
     *             If any internal errors are encountered inside the client while
     *             attempting to make the request or handle the response.  For example
     *             if a network connection is not available.
     * @throws AmazonServiceException
     *             If an error response is returned by AmazonSQS indicating
     *             either a problem with the data in the request, or a server side issue.
     */
    @Processor
    @InvalidateConnectionOn(exception = AmazonClientException.class)
    public void deleteQueue(@Optional String queueUrl) throws AmazonServiceException {
        msgQueue.deleteQueue(new DeleteQueueRequest(getQueueUrl(queueUrl)));
    }

    /**
     * Gets queue attributes. This is provided to expose the underlying functionality.
     * Currently supported attributes are;
     * ApproximateNumberOfMessages
     * CreatedTimestamp
     * LastModifiedTimestamp
     * VisibilityTimeout
     * RequestPayer
     * Policy
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:get-queue-attributes}
     *
     * @param attribute Attribute to get
     * @param queueUrl The URL of the queue
     * @return a map of attributes and their values
     * @throws AmazonClientException
     *             If any internal errors are encountered inside the client while
     *             attempting to make the request or handle the response.  For example
     *             if a network connection is not available.
     * @throws AmazonServiceException
     *             If an error response is returned by AmazonSQS indicating
     *             either a problem with the data in the request, or a server side issue.
     */
    @Processor
    @InvalidateConnectionOn(exception = AmazonClientException.class)
    public Map<String, String> getQueueAttributes(String attribute, @Optional String queueUrl)
            throws AmazonServiceException {
        return msgQueue.getQueueAttributes(
                new GetQueueAttributesRequest(getQueueUrl(queueUrl)).withAttributeNames(attribute)).getAttributes();
    }

    /**
     * Sets a queue attribute. This is provided to expose the underlying functionality, although
     * the only attribute at this time is visibility timeout.
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:set-queue-attribute}
     *
     * @param attribute name of the attribute being set
     * @param value     the value being set for this attribute
     * @param queueUrl The URL of the queue.
     * @throws AmazonClientException
     *             If any internal errors are encountered inside the client while
     *             attempting to make the request or handle the response.  For example
     *             if a network connection is not available.
     * @throws AmazonServiceException
     *             If an error response is returned by AmazonSQS indicating
     *             either a problem with the data in the request, or a server side issue.
     */
    @Processor
    @InvalidateConnectionOn(exception = AmazonClientException.class)
    public void setQueueAttribute(String attribute, String value, @Optional String queueUrl)
            throws AmazonServiceException {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(attribute, value);
        msgQueue.setQueueAttributes(new SetQueueAttributesRequest(getQueueUrl(queueUrl), attributes));
    }

    /**
     * Adds a permission to this message queue.
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:add-permission}
     *
     * @param label     a name for this permission
     * @param accountId the AWS account ID for the account to share this queue with
     * @param action    a value to indicate how much to share (SendMessage, ReceiveMessage, ChangeMessageVisibility, DeleteMessage, GetQueueAttributes)
     * @param queueUrl Permissions will be added to the queue represented by this URL.
     * @throws AmazonClientException
     *             If any internal errors are encountered inside the client while
     *             attempting to make the request or handle the response.  For example
     *             if a network connection is not available.
     * @throws AmazonServiceException
     *             If an error response is returned by AmazonSQS indicating
     *             either a problem with the data in the request, or a server side issue.
     */
    @Processor
    @InvalidateConnectionOn(exception = AmazonClientException.class)
    public void addPermission(String label, String accountId, String action, @Optional String queueUrl)
            throws AmazonServiceException {
        msgQueue.addPermission(new AddPermissionRequest(getQueueUrl(queueUrl), label,
                toList(accountId), toList(action)));
    }

    /**
     * Removes a permission from this message queue.
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:remove-permission}
     *
     * @param label a name for the permission to be removed
     * @param queueUrl Permissions will be deleted from the queue represented by this URL.
     * @throws AmazonClientException
     *             If any internal errors are encountered inside the client while
     *             attempting to make the request or handle the response.  For example
     *             if a network connection is not available.
     * @throws AmazonServiceException
     *             If an error response is returned by AmazonSQS indicating
     *             either a problem with the data in the request, or a server side issue.
     */
    @Processor
    @InvalidateConnectionOn(exception = AmazonClientException.class)
    public void removePermission(String label, @Optional String queueUrl) throws AmazonServiceException {
        msgQueue.removePermission(new RemovePermissionRequest(getQueueUrl(queueUrl), label));
    }   
    
    /**
     * Gets an approximate number of visible messages for a queue.
     * <p/>
     * {@sample.xml ../../../doc/mule-module-sqs.xml.sample sqs:get-approximate-number-of-messages}
     * @param queueUrl The URL of the queue
     * @throws AmazonClientException
     *             If any internal errors are encountered inside the client while
     *             attempting to make the request or handle the response.  For example
     *             if a network connection is not available.
     * @throws AmazonServiceException
     *             If an error response is returned by AmazonSQS indicating
     *             either a problem with the data in the request, or a server side issue.
     * @return the approximate number of messages in the queue
     */
    @Processor
    @InvalidateConnectionOn(exception = AmazonClientException.class)
    public int getApproximateNumberOfMessages(@Optional String queueUrl) throws AmazonServiceException {
        return Integer.parseInt(msgQueue.getQueueAttributes(
                new GetQueueAttributesRequest(getQueueUrl(queueUrl)).withAttributeNames(
                        "ApproximateNumberOfMessages")).getAttributes().get("ApproximateNumberOfMessages"));
    }

    public String getQueueUrl(String queueParameter) {
        if (StringUtils.isNotEmpty(queueParameter)) {
        	return queueParameter;
        }
        if (StringUtils.isNotEmpty(this.queueUrl)) {
        	return this.queueUrl;
        }
        else {
        	return null;
        }
    }

    public void setQueueUrl(String queueUrl) {
        this.queueUrl = queueUrl;
    }

    private List<String> toList(String element) {
        List<String> list = new ArrayList<String>();
        list.add(element);
        return list;
    }

    public RegionEndpoint getRegion() {
        return region;
    }

    public void setRegion(RegionEndpoint region) {
        this.region = region;
    }
}
