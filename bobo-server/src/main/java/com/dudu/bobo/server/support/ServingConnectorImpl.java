package com.dudu.bobo.server.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.dudu.bobo.common.ChannelWrapper;
import com.dudu.bobo.common.Message;
import com.dudu.bobo.common.Node;
import com.dudu.bobo.common.RpcRequest;
import com.dudu.bobo.server.Executor;
import com.dudu.bobo.server.ServingConnector;

/**
 * 
 * @author liangy43
 *
 */
public class ServingConnectorImpl implements ServingConnector, Runnable {

	private static volatile ServingConnector instance = null;

	public static ServingConnector getServingConnector() {
		/*
		 * double check lock
		 */
		if (instance == null) {
			synchronized(ServingConnectorImpl.class) {
				ServingConnectorImpl server = new ServingConnectorImpl();
				server.init();
				instance = server;
			}
		}

		return instance;
	}

	private Executor	executor;
	private Selector	selector = null;

	private InetSocketAddress servingAddress;

	Map<Node, ChannelWrapper> channelMap = new ConcurrentHashMap<Node, ChannelWrapper>();
	
	private void init() {
		try {
			// ��ʼ�������ַ

			ServerSocketChannel servingChannel = ServerSocketChannel.open();  
			servingChannel.configureBlocking(false);
			servingChannel.bind(servingAddress);

	        // ͨ��open()�����ҵ�Selector
	        selector = Selector.open();  
	        // ע�ᵽselector���ȴ�����  
	        servingChannel.register(selector, SelectionKey.OP_ACCEPT);  
	        
			// ����ͨ���߳�
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void setExecutor(Executor executor) {
		this.executor = executor;
	}
	
	public void response(Node target, Message message) {
		channelMap.get(target).sendMessage(message);
	}
	
	public void run() {
		while (true) {
			try {
				// �������޵ȴ�, ��Ϊ����û�пɶ��¼�����Ҫ�������ݵ����
				selector.select(100);
				
				Set<SelectionKey> selectionKeys = selector.selectedKeys();
				Iterator<SelectionKey>iterator = selectionKeys.iterator();  
	            while (iterator.hasNext()) {
	            	SelectionKey selectionKey = iterator.next();

	            	if (selectionKey.isAcceptable()) {
	            		ServerSocketChannel  server = (ServerSocketChannel) selectionKey.channel();
	            		SocketChannel client = server.accept();
	            		client.configureBlocking(false);
	            		
	            		ChannelWrapper clientChannel = new ChannelWrapper(client);
	            		channelMap.put(clientChannel.getPeer(), clientChannel);
	            		
	            		client.register(selector, SelectionKey.OP_READ, clientChannel);
	            	} else if (selectionKey.isReadable()) {
	            		ChannelWrapper clientChannel = (ChannelWrapper) selectionKey.attachment();
	                    Message message = clientChannel.read();
	                    RpcRequest request = (RpcRequest)message.getMessageBody();
	                    executor.dispatch(message, clientChannel.getPeer());
	                } else if (selectionKey.isWritable()) {
	                	ChannelWrapper clientChannel = (ChannelWrapper)selectionKey.attachment();
	                	clientChannel.write();
	                	clientChannel.getChannel().register(selector, SelectionKey.OP_READ, clientChannel);
	                }
	            }

	            for (ChannelWrapper channelWrapper : channelMap.values()) {
	            	if (channelWrapper.hasMessageToSend() == true) {
	            		channelWrapper.getChannel().register(selector,
	            				SelectionKey.OP_WRITE | SelectionKey.OP_READ, channelWrapper);
	            	}
	            }
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} 	
	}
}
