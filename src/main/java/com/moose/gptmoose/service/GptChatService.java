package com.moose.gptmoose.service;


import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Moose
 */
public interface GptChatService {
    void streamChat(String addr ,String content, HttpServletResponse response) throws IOException;
    void createImage(String content);
}
