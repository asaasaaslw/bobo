package com.dudu.bobo.client.support;

import java.util.concurrent.atomic.AtomicLong;

import com.dudu.bobo.client.ClientConnector;
import com.dudu.bobo.client.RpcStub;
import com.dudu.bobo.common.Message;
import com.dudu.bobo.common.Node;
import com.dudu.bobo.common.RpcRequest;
import com.dudu.bobo.common.RpcResponse;

public class RpcStubImpl implements RpcStub {
	
	private Node	target;
	
	private ClientConnector	client;

	// ��������-Ӧ��ƥ��, �����ظ��Ҵ��ھ�������, �ʶ�ʹ��ԭ������
	private static AtomicLong reqId = new AtomicLong(0);

	public RpcStubImpl(Node node) {
		this.target = node;
	}

	public int init() {
		return 0;
	}

	@Override
	public RpcResponse call(RpcRequest rpcRequest) {
		try {
			Message request = new Message(reqId.getAndIncrement(), rpcRequest);
			Message response = client.sendAndReceive(target, request);
			return (RpcResponse)response.getMessageBody();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

}
