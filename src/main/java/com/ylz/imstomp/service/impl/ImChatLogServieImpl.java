package com.ylz.imstomp.service.impl;

import com.ylz.imstomp.bean.ChatMessage;
import com.ylz.imstomp.dao.jpa.ImChatLogDao;
import com.ylz.imstomp.service.ImChatLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service("imChatLogService")
public class ImChatLogServieImpl implements ImChatLogService {

    @Autowired
    private ImChatLogDao imChatLogDao;

    @Override
    public void saveChatLog(ChatMessage chatMessage) {
        log.info("saveChatLog--保存聊天记录");
        imChatLogDao.save(chatMessage);
    }
}