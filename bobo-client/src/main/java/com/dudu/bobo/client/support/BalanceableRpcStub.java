package com.dudu.bobo.client.support;

import java.util.List;

import com.dudu.bobo.client.BalancePolicy;
import com.dudu.bobo.client.RpcStub;
import com.dudu.bobo.common.Node;
import com.dudu.bobo.common.RpcRequest;
import com.dudu.bobo.common.RpcResponse;
import com.dudu.bobo.common.ServiceInfo;

public class BalanceableRpcStub implements RpcStub {

	String 			interfaceName;
	BalancePolicy	policy = new SimpleBalancePolicy();
	
	public void init(ServiceInfo info) {
		List<Node> list = info.getServiceNodes();
		for (Node node: list) {
			RpcStubImpl stub = new RpcStubImpl(node);
			// TODO: �����ʱ��ʼ��ʧ��, �ֵ����?
			if (stub.init() == 0) {
				policy.join(stub);
			}
		}
	}
	
	@Override
	public RpcResponse call(RpcRequest request) throws Exception {
		System.out.println("�Զ����ؿͻ�����Ч!");
	
		RpcStub stub = policy.select();
		if (stub == null) {
			System.out.println("û���ṩ��?");
			throw new Exception("û���ṩ��");
		}
		RpcResponse response;
		try {
			response = stub.call(request);
			return response;
		} catch (Exception ex) {
			policy.doubt(stub);
			return null;
		}
	}

}
