/*
 * Copyright (C) 2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.converse;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
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
	private static final Log logger = LogFactory.getLog(TurConverse.class);
	@Autowired
	private TurConverseChatResponseRepository turConverseChatResponseRepository;
	@Autowired
	private TurConverseSE turConverseSE;

	final static String FALLBACK_DEFAULT_MESSAGE = "I do not know what to say";

	public void saveChatResponseUser(String q, TurConverseChat chat, HttpSession session) {
		boolean hasParameter = session.getAttribute("hasParameter") != null
				? (boolean) session.getAttribute("hasParameter")
				: false;

		TurConverseChatResponse chatResponseUser = new TurConverseChatResponse();
		chatResponseUser.setDate(new Date());
		chatResponseUser.setUser(true);
		chatResponseUser.setText(q);
		chatResponseUser.setChat(chat);

		TurConverseChatResponse chatResponseBot = (TurConverseChatResponse) session.getAttribute("previousResponseBot");
		if (hasParameter && chatResponseBot != null) {
			chatResponseUser.setIntentId(chatResponseBot.getIntentId());
			chatResponseUser.setActionName(chatResponseBot.getActionName());
			chatResponseUser.setParameterName(chatResponseBot.getParameterName());
			chatResponseUser.setParameterValue(q);
		} else {
			String nextContext = (String) session.getAttribute("nextContext");
			SolrDocumentList results = turConverseSE.solrAskPhrase(chat.getAgent(), q, nextContext);
			if (!results.isEmpty()) {
				SolrDocument firstResult = results.get(0);
				String intentId = (String) firstResult.getFieldValue("id");
				SimpleEntry<String, String> parameter = this.getParameterValue(q, chat, intentId);
				if (parameter != null) {
					chatResponseUser.setParameterName(parameter.getKey());
					chatResponseUser.setParameterValue(parameter.getValue());
				}
			}
		}

		turConverseChatResponseRepository.save(chatResponseUser);

		session.setAttribute("previousResponseUser", chatResponseUser);

	}

	public AbstractMap.SimpleEntry<String, String> getParameterValue(String text, TurConverseChat turConverseChat,
			String intentId) {
		SolrDocumentList termDocumentList = turConverseSE.sorlGetParameterValue(text, turConverseChat, intentId);
		if (!termDocumentList.isEmpty()) {
			String parameter = (String) termDocumentList.get(0).getFirstValue("parameters");

			String[] parameterKV = parameter.split(":");
			AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<>(parameterKV[0],
					parameterKV[1]);

			return entry;

		}
		return null;
	}

	public void saveChatResponseBot(TurConverseChat chat, TurConverseAgentResponse turConverseAgentResponse,
			HttpSession session) {
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
			TurConverseChatResponse chatResponseUser = (TurConverseChatResponse) session
					.getAttribute("previousResponseUser");
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
					if (!prompts.isEmpty()) {
						int rnd = new Random().nextInt(prompts.size());
						turConverseAgentResponse.setResponse(prompts.get(rnd).toString());
						turConverseAgentResponse.setIntentId(intent);
						turConverseAgentResponse.setActionName(firstResultParameter.getFieldValue("action").toString());
						turConverseAgentResponse
								.setParameterName(firstResultParameter.getFieldValue("name").toString());
					}

				}
				session.setAttribute("nextParameter", nextParameter + 1);
			}

			if (resultsParameter.size() == nextParameter) {
				this.getIntentWhenFinishParameters(chat, session, turConverseAgentResponse, intent);
			}
		} catch (SolrServerException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public TurConverseAgentResponse interactionNested(TurConverseChat chat, String q, String nextContext,
			HttpSession session) {

		TurConverseAgentResponse turConverseAgentResponse = new TurConverseAgentResponse();
		SolrDocumentList results = turConverseSE.solrAskPhraseFallback(chat.getAgent(), q, nextContext);
		if (results.isEmpty())
			results = turConverseSE.solrAskPhrase(chat.getAgent(), q, nextContext);

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

		return turConverseAgentResponse;
	}

	@SuppressWarnings("unchecked")
	private void getIntentFlow(TurConverseChat chat, HttpSession session,
			TurConverseAgentResponse turConverseAgentResponse, SolrDocument firstResult) {

		List<String> contextOutputs = (List<String>) firstResult.getFieldValue("contextOutput");

		turConverseAgentResponse.setResponse(this.getIntentResponse(chat, session, firstResult));
		turConverseAgentResponse.setIntentId(firstResult.getFieldValue("id").toString());
		turConverseAgentResponse.setIntentName(firstResult.getFieldValue("name").toString());

		if (contextOutputs != null && !contextOutputs.isEmpty()) {
			session.setAttribute("nextContext", contextOutputs.get(0).toString());
		}
	}

	@SuppressWarnings("unchecked")
	private String getIntentResponse(TurConverseChat chat, HttpSession session, SolrDocument firstResult) {
		List<String> responses = (List<String>) firstResult.getFieldValue("responses");
		int rnd = new Random().nextInt(responses.size());
		String response = responses.get(rnd).toString();

		String[] words = response.split(" ");
		StringBuffer responseModified = new StringBuffer();
		for (String word : words) {
			if (word.startsWith("$")) {
				String parameterName = word.replaceAll("\\$", "").replaceAll(",", "").replaceAll(";", "")
						.replaceAll("\\.", "");

				List<TurConverseChatResponse> values = turConverseChatResponseRepository
						.findByChatAndIsUserAndParameterNameOrderByDateDesc(chat, true, parameterName);
				if (!values.isEmpty()) {
					word = values.get(0).getParameterValue();
				}
			}
			responseModified.append(word + " ");
		}

		return responseModified.toString();
	}

	private TurConverseAgentResponse getFallback(TurConverseChat chat, HttpSession session,
			TurConverseAgentResponse turConverseAgentResponse) {
		this.cleanSession(session);
		turConverseAgentResponse.setResponse(this.getFallbackResponse(chat));
		turConverseAgentResponse.setIntentName("empty");

		return turConverseAgentResponse;
	}

	private String getFallbackResponse(TurConverseChat chat) {
		SolrDocumentList fallbackList = turConverseSE.solrGetFallbackIntent(chat.getAgent());
		if (fallbackList.isEmpty())
			return FALLBACK_DEFAULT_MESSAGE;
		else {
			SolrDocument fallbackIntent = fallbackList.get(0);

			@SuppressWarnings("unchecked")
			List<String> responses = (List<String>) fallbackIntent.getFieldValue("responses");

			if (responses != null && !responses.isEmpty()) {
				int rnd = new SecureRandom().nextInt(responses.size());
				return responses.get(rnd).toString();
			} else {
				return FALLBACK_DEFAULT_MESSAGE;
			}
		}
	}

	private void getIntentWhenFinishParameters(TurConverseChat chat, HttpSession session,
			TurConverseAgentResponse turConverseAgentResponse, String intent) {

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
