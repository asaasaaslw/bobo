package com.dudu.bobo.common;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * 
 * @author liangy43
 *
 */
public class StreamReader {
	private final SocketChannel	channel;

	private final int	readBufferSize	= 65536;
	private ByteBuffer	readBuffer		= ByteBuffer.allocate(readBufferSize);
	private boolean 	corrupted		= false;
	private int     	msgLen			= 0;
	
	public StreamReader(SocketChannel channel) {
		this.channel = channel;
	}
	
	public Message read() {
		try {
			int count = channel.read(readBuffer);
	        if (count > 0) {
	        	int len = 0;
	        	// ȷ����Ϣ����
	        	if (corrupted == false) {
	        		// ����4�ֽ�?
	            	if (readBuffer.limit() - readBuffer.position() < 4) {
	            		return null;
	            	}
	            	// ��ȡ��Ϣ����
	            	len = readBuffer.getInt();	
	        	} else {
	        		len = msgLen;
	        	}
	        	
	        	// ����������Ϣ������, ����������ӵı��ζ�ȡ, ���´ζ�ȡ
	        	if (readBuffer.remaining() < len) {
	        		msgLen = len;
	        		corrupted = true;
	        		return null;
	        	} else {
	        		msgLen = 0;
	        		corrupted = false;
	        	}
	        	
	        	// ��ȡ��Ϣ
	        	byte[] bytes = new byte[len];
	        	readBuffer.get(bytes);

	            // ������Ϊ����
	        	ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
	            ObjectInputStream ois = new ObjectInputStream(bais);
	            Message message = (Message) ois.readObject();
				return message;
	        }
		} catch (IOException ioex) {

		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        return null;
	}
}
