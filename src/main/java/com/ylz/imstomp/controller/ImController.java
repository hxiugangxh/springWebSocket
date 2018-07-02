package com.ylz.imstomp.controller;

import com.ylz.imstomp.bean.ChatMessage;
import com.ylz.imstomp.bean.ImUser;
import com.ylz.imstomp.bean.OnlineInfoBean;
import com.ylz.imstomp.constant.AMQConstants;
import com.ylz.imstomp.constant.Constants;
import com.ylz.imstomp.dao.mongodb.ImChatLogMongoJpa;
import com.ylz.imstomp.dao.mongodb.ImChatMongoDao;
import com.ylz.imstomp.service.ImChatLogService;
import com.ylz.imstomp.service.ImService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/im")
public class ImController {

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate;

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ImService imService;

    @Autowired
    private ImChatLogService imChatLogService;

    @Autowired
    private ImChatLogMongoJpa imChatLogMongo;

    @RequestMapping("/look")
    @ResponseBody
    public String look() {

        Cache cache = cacheManager.getCache("websocket_account");

        System.out.println("admin = " + cache.get("websocket_accountadmin", String.class));
        System.out.println("abel = " + cache.get("websocket_accountabel", String.class));

        return "test";
    }

    @RequestMapping("/me")
    @ResponseBody
    public Object me(Authentication authentication) {

        return authentication;
    }

    @RequestMapping("/chatRoom")
    public String chatRoom(Map<String, Object> map, Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        String userName = userDetails.getUsername();
        ImUser imUser = new ImUser();
        imUser.setUserName(userName);
        imUser.setNick(imService.getNick(userName));

        map.put("imUser", imUser);

        return "chat_room";
    }

    @RequestMapping("/listChatMessage")
    @ResponseBody
    public List<ChatMessage> listChatMessage(
            @RequestParam("type") Integer type,
            @RequestParam(value = "fromUserName", required = false) String fromUserName,
            @RequestParam(value = "toUserName", required = false) String toUserName,
            @RequestParam(value = "pn") Integer pn,
            @RequestParam(value = "pageSize") Integer pageSize) {

        if (type == 0) {
            imService.readChatMessage(fromUserName, toUserName);
            log.info("发送已读fromUserName = {}", fromUserName);
            simpMessagingTemplate.convertAndSendToUser(toUserName,
                    Constants.SINGLE_CHAT_DES_READ, "read");
        }

        return imService.listChatMessage(type, fromUserName, toUserName, pn, pageSize);
    }

    @RequestMapping("/readChatMessage")
    @ResponseBody
    public Map<String, Object> readChatMessage(
            @RequestParam(value = "fromUserName") String fromUserName,
            @RequestParam(value = "toUserName") String toUserName) {

        log.info("readChatMessage");
        imService.readChatMessage(toUserName, fromUserName);
        log.info("toUserName = {}", toUserName);
        simpMessagingTemplate.convertAndSendToUser(fromUserName,
                Constants.SINGLE_CHAT_DES_READ, "read");

        Map<String, Object> jsonMap = new HashMap<>();

        jsonMap.put("flag", true);

        return jsonMap;
    }

    @RequestMapping("/brokerOnline")
    @ResponseBody
    public Map<String, Object> brokerOnline() {
        Map<String, Object> jsonMap = new HashMap<>();
        boolean flag = true;
        try {
            List<String> onlineUserList = new ArrayList<>();
            for (SimpUser simpUser : simpUserRegistry.getUsers()) {
                onlineUserList.add(simpUser.getName());
            }

            OnlineInfoBean onlineInfoBean = imService.listOnlineUser(onlineUserList);

            log.info("brokerOnline--广播在线信息: {}", onlineInfoBean);
            simpMessagingTemplate.convertAndSend(Constants.USER_ONLINE_INFO, onlineInfoBean);
        } catch (Exception e) {
            e.printStackTrace();
            flag = false;
        }

        jsonMap.put("flag", flag);

        return jsonMap;
    }

    @RequestMapping("/groupChat")
    @ResponseBody
    public Map<String, Object> groupChat(ChatMessage chatMessage,
                                         @RequestParam(value = "action", defaultValue = "sendMsg") String action) {
        Map<String, Object> jsonMap = new HashMap<>();
        if ("read".equals(action)) {
            imService.readChatMessage(chatMessage.getFromUserName(), chatMessage.getToUserName());

            jsonMap.put("flag", true);
            return jsonMap;
        }
        // 默认群聊为已读
        chatMessage.setReadFlag(1);

        boolean flag = true;
        try {
            log.info("groupChat--保存聊天记录: {}", chatMessage);
            imChatLogService.saveChatLog(chatMessage);

            log.info("groupChat--发送聊天信息: {}", chatMessage);
            simpMessagingTemplate.convertAndSend(Constants.GROUP_CHAT_DES, chatMessage);
        } catch (Exception e) {
            e.printStackTrace();
            flag = false;
        }
        jsonMap.put("flag", flag);

        return jsonMap;
    }

    @RequestMapping("/chatSingleRoom")
    public String chatSingleRoom(
            @RequestParam(value = "toUserName") String toUserName,
            Map<String, Object> map,
            Principal principal) {

        ImUser imUser = new ImUser();
        imUser.setUserName(principal.getName());
        imUser.setNick(imService.getNick(principal.getName()));

        ImUser toImUser = new ImUser();
        toImUser.setUserName(toUserName);
        toImUser.setNick(imService.getNick(toUserName));

        map.put("imUser", imUser);
        map.put("toImUser", toImUser);

        return "chat_single_room";
    }

    @RequestMapping("/singleChat")
    @ResponseBody
    public Map<String, Object> singleChat(ChatMessage chatMessage, Principal principal) {
        chatMessage.setFromUserName(principal.getName());
        chatMessage.setFromNick(imService.getNick(principal.getName()));

        Map<String, Object> jsonMap = new HashMap<>();
        boolean flag = true;
        try {
            log.info("singleChat--保存聊天记录: {}", chatMessage);
            imChatLogService.saveChatLog(chatMessage);

            log.info("singleChat--发送聊天信息: {}", chatMessage);
            simpMessagingTemplate.convertAndSendToUser(chatMessage.getToUserName(),
                    Constants.SINGLE_CHAT_DES, chatMessage);
        } catch (Exception e) {
            e.printStackTrace();
            flag = false;
        }
        jsonMap.put("flag", flag);

        return jsonMap;
    }

}
