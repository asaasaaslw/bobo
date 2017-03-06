package com.dudu.bobo.client.support;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.dudu.bobo.client.ClientConnector;
import com.dudu.bobo.common.ChannelWrapper;
import com.dudu.bobo.common.Message;
import com.dudu.bobo.common.Node;
import com.dudu.bobo.common.NodeImpl;

/**
 * ͨ�ſͻ���, ʵ��ͨ���Լ�����-Ӧ���ƥ��
 * 
 * @author liangy43
 *
 */
public class ClientConnectorImpl implements ClientConnector, Runnable {
	
	private static volatile ClientConnector instance = null;
	
	public static ClientConnector getClient() {
		/*
		 * double check lock
		 */
		if (instance == null) {
			synchronized(ClientConnectorImpl.class) {
				ClientConnectorImpl client = new ClientConnectorImpl();
				client.init();
				instance = client;
			}
		}

		return instance;
	}
	
	// ��������-Ӧ��ƥ��, �����ظ��Ҵ��ھ�������, �ʶ�ʹ��ԭ������
	private AtomicLong reqId = new AtomicLong(0);
	
	private Map<Node, ChannelWrapper> channelMap
				= new ConcurrentHashMap<Node, ChannelWrapper>();
	
	private Selector selector = null;
	
	private void init() {
		try {
			// ��ʼ��selector����
			selector = Selector.open();
			
			// ����ͨ���߳�
			Thread t = new Thread(this);
			t.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public boolean isConnected(Node node) {
		return channelMap.containsKey(node);
	}

	public int open(Node node) {
		try {
			// ��
			SocketChannel channel = SocketChannel.open();
			// ���÷�����
			channel.configureBlocking(false);
			channel.register(selector, SelectionKey.OP_CONNECT);
			// ����
			channel.connect(((NodeImpl)node).getAddr());
		} catch (ClosedChannelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return 0;
	}
	
	Map<Long, FutureImpl> pendingQueue = new HashMap<Long, FutureImpl>();
	
	/**
	 * 
	 * @return
	 */
	@Override
	public Future<?> send(Node target, Object request) {
		Message req = new Message(reqId.getAndIncrement(), request);
		FutureImpl future = new FutureImpl();
		pendingQueue.put(req.getMessageId(), future);
		channelMap.get(target).sendMessage(req);
		return future;
	}
	
	/**
	 * 
	 */
	@Override
	public Object sendAndReceive(Node target, Object request) {
		try {
			return sendAndReceive(target, request, -1);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public Object sendAndReceive(Node target, Object request, long timeout) throws Exception {
		Future<?> future = send(target, request);
		return future.get(timeout, TimeUnit.MILLISECONDS);
	}

	/**
	 * IO��·���ô����߳�
	 */
	@Override
	public void run() {
		while (true) {
			try {
				// �������޵ȴ�, ��Ϊ����û�пɶ��¼�����Ҫ�������ݵ����
				selector.select(100);
				
				Set<SelectionKey> selectionKeys = selector.selectedKeys();
				Iterator<SelectionKey>iterator = selectionKeys.iterator();  
	            while (iterator.hasNext()) {
	            	SelectionKey selectionKey = iterator.next();

	            	if (selectionKey.isConnectable()) {
	                	SocketChannel channel = (SocketChannel) selectionKey.channel();
	                	ChannelWrapper channelWrapper = new ChannelWrapper(channel);
	                	channelMap.put(channelWrapper.getPeer(), channelWrapper);
	                	channel.register(selector, SelectionKey.OP_READ, channelWrapper);
	            	} else if (selectionKey.isReadable()) {
	            		ChannelWrapper serverChannel = (ChannelWrapper) selectionKey.attachment();
	            		Message response = serverChannel.read();
	                    // ��ȡ�����ʶ
	                    long msgId = response.getMessageId();
	                    FutureImpl future = pendingQueue.get(msgId);       	
	                    // ��δ������ɾ��
	                    pendingQueue.remove(msgId);
	                    // ֪ͨ
	                    future.signal(response.getMessageBody());
	                } else if (selectionKey.isWritable()) {
	                	ChannelWrapper serverChannel = (ChannelWrapper) selectionKey.attachment();
	                	serverChannel.write();
	                	serverChannel.getChannel().register(selector, SelectionKey.OP_READ, serverChannel);
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
