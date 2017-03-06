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
import com.dudu.bobo.common.Message;
import com.dudu.bobo.common.Node;

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
	
	class MessageWithFuture {

		private Message message;

		transient FutureImpl future = new FutureImpl();
		
		public MessageWithFuture(long andIncrement, Object request) {
			message = new Message(andIncrement, request);
		}

		public Message getMessage() {
			return this.message;
		}
		
		public Long getMessageId() {
			return this.message.getMessageId();
		}
		
		FutureImpl getFuture() {
			return future;
		}
	}
	
	class ChannelWrapper {
		SocketChannel channel;
		List<MessageWithFuture> sendQueue = new LinkedList<MessageWithFuture>();
		Map<Long, MessageWithFuture> pendingQueue = new HashMap<Long, MessageWithFuture>();
	
		ChannelWrapper(SocketChannel channel) {
			this.channel = channel;
		}

		List<MessageWithFuture> getSendQueue() {
			return sendQueue;
		}

		Map<Long, MessageWithFuture> getPendingQueue() {
			return this.pendingQueue;
		}
		
		SocketChannel getChannel() {
			return channel;
		}
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
	
	/**
	 * 
	 * @return
	 */
	@Override
	public Future<?> send(Node target, Object request) {
		MessageWithFuture req = new MessageWithFuture(reqId.getAndIncrement(), request);
		channelMap.get(target).getSendQueue().add(req);
		return req.getFuture();
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
		ByteBuffer readBuffer = ByteBuffer.allocate(65536);
		boolean interruptd = false;
		int     msgLen = 0;
		ByteBuffer writeBuffer = ByteBuffer.allocate(65536);
		
		try {
			while (true) {
				// �������޵ȴ�, ��Ϊ����û�пɶ��¼�����Ҫ�������ݵ����
				selector.select(100);
				
				Set<SelectionKey> selectionKeys = selector.selectedKeys();
				Iterator<SelectionKey>iterator = selectionKeys.iterator();  
	            while (iterator.hasNext()) {
	            	SelectionKey selectionKey = iterator.next();

	            	if (selectionKey.isConnectable()) {
	                	SocketChannel channel = (SocketChannel) selectionKey.channel();
	                	ChannelWrapper channelWrapper = new ChannelWrapper(channel);
	                	channelMap.put(new NodeImpl((InetSocketAddress)channel.getRemoteAddress()),
	                			channelWrapper);
	                	channel.register(selector, SelectionKey.OP_READ, channelWrapper);
	            	} else if (selectionKey.isReadable()) {
	                	SocketChannel channel = (SocketChannel) selectionKey.channel();

	            		int count = channel.read(readBuffer);
	                    if (count > 0) {
	                    	int len = 0;
	                    	// ȷ����Ϣ����
	                    	if (interruptd == false) {
	                    		// ����4�ֽ�?
		                    	if (readBuffer.limit() - readBuffer.position() < 4) {
		                    		continue;
		                    	}
		                    	// ��ȡ��Ϣ����
		                    	len = readBuffer.getInt();	
	                    	} else {
	                    		len = msgLen;
	                    	}
	                    	
	                    	// ����������Ϣ������, ����������ӵı��ζ�ȡ, ���´ζ�ȡ
	                    	if (readBuffer.remaining() < len) {
	                    		msgLen = len;
	                    		interruptd = true;
	                    		continue;
	                    	} else {
	                    		msgLen = 0;
	                    		interruptd = false;
	                    	}
	                    	
	                    	// ��ȡ��Ϣ
	                    	byte[] bytes = new byte[len];
	                    	readBuffer.get(bytes);
	                    	
	                        // ������Ϊ����
	                    	ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
	                        ObjectInputStream ois = new ObjectInputStream(bais);
	                        Object obj = null;
							try {
								obj = (Message)ois.readObject();
							} catch (ClassNotFoundException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
	                        Message response = (Message)obj;
	                        
	                    	// ��ȡ�����ʶ
	                        long msgId = response.getMessageId();
	                    	
	                    	// ��ȡfuture
	                    	ChannelWrapper queuedChannel = (ChannelWrapper)selectionKey.attachment();
	                    	Map<Long, MessageWithFuture> pendingQueue = queuedChannel.getPendingQueue();
	                    	MessageWithFuture m = pendingQueue.get(msgId);
	                    	FutureImpl future = m.getFuture();
	    	                    	
	                    	// ��δ������ɾ��
	                    	pendingQueue.remove(m);

	    	                // ֪ͨ
	    	                future.signal(response.getMessageBody());
  	                    }
	                } else if (selectionKey.isWritable()) {
	                	ChannelWrapper channelWrapper = (ChannelWrapper)selectionKey.attachment();

	                	writeBuffer.clear();
	                	
	                	// ���Ͷ��������Ϣһ�η���
	                	List<MessageWithFuture> sendQueue = channelWrapper.getSendQueue();
	                	for (MessageWithFuture req : sendQueue) {
	                		ByteArrayOutputStream baos = new ByteArrayOutputStream();
	                		ObjectOutputStream oos = new ObjectOutputStream(baos);
	                		oos.writeObject(req.getMessage());
	                		byte[] bytes = baos.toByteArray();
		                    writeBuffer.putInt(bytes.length);
		                    writeBuffer.put(bytes);
		                    System.out.println("�ͻ�����������˷�������--��"+ req);
		                    // �ӷ��Ͷ���ɾ��
		                    sendQueue.remove(req);
		                    // ��ӵ�δ������
		                    channelWrapper.getPendingQueue().put(req.getMessageId(), req);
	                	}
	                	writeBuffer.flip();
	                	channelWrapper.getChannel().write(writeBuffer);
	                	channelWrapper.getChannel().register(selector, SelectionKey.OP_READ, channelWrapper);
	                }
	            }

	            for (ChannelWrapper channelWrapper : channelMap.values()) {
	            	if (channelWrapper.getSendQueue().isEmpty() == false) {
	            		channelWrapper.getChannel().register(selector,
	            				SelectionKey.OP_WRITE | SelectionKey.OP_READ, channelWrapper);
	            	}
	            }
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
