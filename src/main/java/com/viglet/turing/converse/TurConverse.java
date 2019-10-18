package com.viglet.turing.converse;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.servlet.http.HttpSession;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.bean.converse.TurConverseAgentResponse;
import com.viglet.turing.persistence.model.converse.chat.TurConverseChat;
import com.viglet.turing.persistence.model.converse.chat.TurConverseChatResponse;
import com.viglet.turing.persistence.repository.converse.chat.TurConverseChatResponseRepository;

@Component
public class TurConverse {
	@Autowired
	private TurConverseChatResponseRepository turConverseChatResponseRepository;
	@Autowired
	private TurConverseSE turConverseSE;
	
	public void saveChatResponseUser(String q, TurConverseChat chat, HttpSession session) {
		boolean hasParameter = session.getAttribute("hasParameter") != null
				? (boolean) session.getAttribute("hasParameter")
				: false;
				
		TurConverseChatResponse chatResponseUser = new TurConverseChatResponse();
		chatResponseUser.setDate(new Date());
		chatResponseUser.setUser(true);
		chatResponseUser.setText(q);
		chatResponseUser.setChat(chat);
		if (hasParameter && session.getAttribute("previousResponseBot") != null) {
			TurConverseChatResponse chatResponseBot = (TurConverseChatResponse) session.getAttribute("previousResponseBot");
			chatResponseUser.setIntentId(chatResponseBot.getIntentId());
			chatResponseUser.setActionName(chatResponseBot.getActionName());
			chatResponseUser.setParameterName(chatResponseBot.getParameterName());					
		}
		turConverseChatResponseRepository.save(chatResponseUser);
		session.setAttribute("previousResponseUser", chatResponseUser);
	}

	public void saveChatResponseBot(TurConverseChat chat, TurConverseAgentResponse turConverseAgentResponse, HttpSession session) {
		TurConverseChatResponse chatResponseBot = new TurConverseChatResponse();
		chatResponseBot.setDate(new Date());
		chatResponseBot.setUser(false);
		chatResponseBot.setText(turConverseAgentResponse.getResponse());
		chatResponseBot.setChat(chat);
		chatResponseBot.setIntentId(turConverseAgentResponse.getIntentId());
		chatResponseBot.setParameterName(turConverseAgentResponse.getParameterName());
		chatResponseBot.setActionName(turConverseAgentResponse.getActionName());

		session.setAttribute("previousResponseBot", chatResponseBot);
		
		turConverseChatResponseRepository.save(chatResponseBot);
		
		if (session.getAttribute("previousResponseUser") != null) {
			TurConverseChatResponse chatResponseUser = (TurConverseChatResponse) session.getAttribute("previousResponseUser");
			if (chatResponseUser.getActionName() == null && chatResponseUser.getIntentId() == null) {
				chatResponseUser.setIntentId(turConverseAgentResponse.getIntentId());
				turConverseChatResponseRepository.save(chatResponseUser);
			}
		}
		
	}

	@SuppressWarnings("unchecked")
	public void getChatParameter(TurConverseChat chat, HttpSession session,
			TurConverseAgentResponse turConverseAgentResponse) {

		int nextParameter = session.getAttribute("nextParameter") != null ? (int) session.getAttribute("nextParameter")
				: 0;

		String intent = session.getAttribute("intent") != null ? (String) session.getAttribute("intent") : null;

		try {
			SolrDocumentList resultsParameter = turConverseSE.solrGetActionAndParameters(chat.getAgent(), intent);
			if (resultsParameter.size() > nextParameter) {
				SolrDocument firstResultParameter = resultsParameter.get(nextParameter);
				if (!resultsParameter.isEmpty()) {
					List<String> prompts = (List<String>) firstResultParameter.getFieldValue("prompts");
					int rnd = new Random().nextInt(prompts.size());
					turConverseAgentResponse.setResponse(prompts.get(rnd).toString());
					turConverseAgentResponse.setIntentId(intent);
					turConverseAgentResponse.setActionName(firstResultParameter.getFieldValue("action").toString());
					turConverseAgentResponse.setParameterName(firstResultParameter.getFieldValue("name").toString());
									
				}
				session.setAttribute("nextParameter", nextParameter + 1);
			}

			if (resultsParameter.size() == nextParameter) {
				this.getIntentWhenFinishParameters(chat, session, turConverseAgentResponse, intent);
			}
		} catch (SolrServerException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
	public TurConverseAgentResponse interactionNested(TurConverseChat chat, String q,
			String nextContext, HttpSession session) {

		TurConverseAgentResponse turConverseAgentResponse = new TurConverseAgentResponse();

		try {
			SolrDocumentList results =  turConverseSE.solrAskPhrase(chat.getAgent(), q, nextContext);
			if (!results.isEmpty()) {
				SolrDocument firstResult = results.get(0);

				if ((boolean) firstResult.getFieldValue("hasParameters")) {
					session.setAttribute("intent", firstResult.getFieldValue("id"));
					session.setAttribute("hasParameter", true);
					this.getChatParameter(chat, session, turConverseAgentResponse);
				} else {
					this.getIntentFlow(chat, session, turConverseAgentResponse, firstResult);
				}
			} else {
				if (nextContext != null) {
					turConverseAgentResponse = this.interactionNested(chat, q, null, session);
				} else {
					turConverseAgentResponse = this.getFallback(chat, session, turConverseAgentResponse);
				}
			}

		} catch (SolrServerException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return turConverseAgentResponse;
	}


	@SuppressWarnings("unchecked")
	private void getIntentFlow(TurConverseChat chat, HttpSession session, TurConverseAgentResponse turConverseAgentResponse,
			SolrDocument firstResult) {
		List<String> responses = (List<String>) firstResult.getFieldValue("responses");
		int rnd = new Random().nextInt(responses.size());

		List<String> contextOutputs = (List<String>) firstResult.getFieldValue("contextOutput");

		turConverseAgentResponse.setResponse(responses.get(rnd).toString());
		turConverseAgentResponse.setIntentId(firstResult.getFieldValue("id").toString());
		turConverseAgentResponse.setIntentName(firstResult.getFieldValue("name").toString());
		
		if (contextOutputs != null && !contextOutputs.isEmpty()) {
			session.setAttribute("nextContext", contextOutputs.get(0).toString());
		}
	}

	private TurConverseAgentResponse getFallback(TurConverseChat chat, HttpSession session,
			TurConverseAgentResponse turConverseAgentResponse) {
		this.cleanSession(session);
		turConverseAgentResponse.setResponse("Não sei o que dizer");
		turConverseAgentResponse.setIntentName("empty");

		return turConverseAgentResponse;
	}

	
	private void getIntentWhenFinishParameters(TurConverseChat chat, HttpSession session,
			TurConverseAgentResponse turConverseAgentResponse, String intent){

		this.cleanParameter(session);

		SolrDocumentList results = turConverseSE.solrGetIntent(chat.getAgent(), intent);
		if (!results.isEmpty()) {
			SolrDocument firstResult = results.get(0);
			this.getIntentFlow(chat, session, turConverseAgentResponse, firstResult);
		}
	}

	public void showSession(HttpSession session) {
		System.out.println("hasParameter: " + (Boolean) session.getAttribute("hasParameter"));
		System.out.println("nextParameter: " + (Integer) session.getAttribute("nextParameter"));
		System.out.println("nextContext: " + (String) session.getAttribute("nextContext"));
		System.out.println("intent: " + (String) session.getAttribute("intent"));
		System.out.println("-----");
	}

	public void cleanSession(HttpSession session) {
		System.out.println("Clean Session");
		session.removeAttribute("hasParameter");
		session.removeAttribute("nextParameter");
		session.removeAttribute("nextContext");
		session.removeAttribute("intent");

	}

	private void cleanParameter(HttpSession session) {
		session.removeAttribute("hasParameter");
		session.removeAttribute("nextParameter");
		session.removeAttribute("intent");

	}
}
