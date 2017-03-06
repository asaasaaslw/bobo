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
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.dudu.bobo.common.Message;
import com.dudu.bobo.common.Node;
import com.dudu.bobo.common.RpcRequest;
import com.dudu.bobo.common.RpcResponse;
import com.dudu.bobo.server.ServingConnector;

/**
 * 
 * @author liangy43
 *
 */
public class ServingConnectorImpl implements ServingConnector, Runnable {

	private static volatile ServingConnector instance = null;
	
	public static ServingConnector getServer() {
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

	private Selector selector = null;
	
	private InetSocketAddress servingAddress;

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

	            	if (selectionKey.isAcceptable()) {
	            		ServerSocketChannel  server = (ServerSocketChannel) selectionKey.channel();
	            		SocketChannel client = server.accept();
	            		client.configureBlocking(false);
	            		
	            		channelMap.put(new NodeImpl((InetSocketAddress)client.getRemoteAddress()),
	                			channelWrapper);
	            		
	            		client.register(selector, SelectionKey.OP_READ, client);
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
	                        Message message = (Message)obj;
	                        
	                        RpcRequest request = (RpcRequest)message.getMessageBody();
	                        
	                    	// ��������

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

	public void response(Node target, RpcResponse response) {
		// TODO Auto-generated method stub
		
	}

}
