package com.moose.gptmoose.service.impl;

import com.google.common.cache.Cache;
import com.moose.gptmoose.enums.RoleEnum;
import com.moose.gptmoose.service.GptChatService;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.image.CreateImageRequest;
import com.theokanning.openai.service.OpenAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * @author Moose
 */
@Service
@Slf4j
public class GptChatServiceImpl implements GptChatService {

//
//    static {
//        System.setProperty("http.proxyHost", "127.0.0.1");
//        System.setProperty("http.proxyPort", "7890");
//        System.setProperty("https.proxyHost", "127.0.0.1");
//        System.setProperty("https.proxyPort", "7890");
//    }
    private Cache<String, LinkedList<ChatMessage>> cache;

    private static final Random RANDOM = new Random();

    @Autowired
    public GptChatServiceImpl(Cache<String, LinkedList<ChatMessage>> cache) {
        this.cache = cache;
    }

    @Value("${gpt.code}")
    private String key ;
    @Override
    public void streamChat(String addr, String content, HttpServletResponse response) throws IOException {
        // 需要指定response的ContentType为流式输出，且字符编码为UTF-8
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        // 禁用缓存
        response.setHeader("Cache-Control", "no-cache");

        //代理设置
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", "7890");
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyPort", "7890");
        OpenAiService service = new OpenAiService(key, Duration.ofSeconds(10));
        log.info("请求消息：{}", content);
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model("gpt-3.5-turbo-1106")
//                .model("gpt-4")
                .messages(Collections.singletonList(new ChatMessage(RoleEnum.USER.getRoleName(), content)))
                .n(1)
                .user(addr)
                .maxTokens(1000)
                .logitBias(new HashMap<>())
                .build();

        LinkedList<ChatMessage> contextInfo = new LinkedList<>();
        try {
            contextInfo = cache.get(addr, LinkedList::new);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        contextInfo.addAll(chatCompletionRequest.getMessages());
        contextInfo.removeIf(Objects::isNull);
        chatCompletionRequest.setMessages(contextInfo);

        ServletOutputStream os = response.getOutputStream();
        List<ChatCompletionChunk> chunks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            try {
                // avoid frequently request, random sleep 0.5s~0.7s
                if (i > 0) {
                    randomSleep();
                }
                service.streamChatCompletion(chatCompletionRequest)
                        .doOnError(Throwable::printStackTrace)
                        .blockingForEach(chunk -> {
                            chunk.getChoices().stream().map(choice -> choice.getMessage().getContent())
                                    .filter(Objects::nonNull).findFirst().ifPresent(o -> {
                                        try {
                                            os.write(o.getBytes(Charset.defaultCharset()));
                                            os.flush();
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    });
                            chunks.add(chunk);
                        });
                break;

            } catch (Exception e) {
                String message = e.getMessage();
                boolean overload = checkTokenUsage(message);
                if (overload) {
                    //如果单次token超了，则吧message剪掉一般
                    int size = Objects.requireNonNull(cache.getIfPresent(addr)).size();
                    for (int j = 0; j < size / 2; j++) {
                        Objects.requireNonNull(cache.getIfPresent(addr)).removeFirst();
                    }
                    chatCompletionRequest.setMessages(cache.getIfPresent(addr));
                }
                if (i==2){
                    Objects.requireNonNull(cache.getIfPresent(addr)).removeLast();
                    throw new RuntimeException(e);
                }
            }finally {
                os.close();
            }
        }

        StringBuilder responseMsg = new StringBuilder();
        List<List<ChatCompletionChoice>> collect = chunks.stream().map(ChatCompletionChunk::getChoices).collect(Collectors.toList());
        for (List<ChatCompletionChoice> chatCompletionChoices : collect) {
            for (ChatCompletionChoice chatCompletionChoice : chatCompletionChoices) {
                if (null != chatCompletionChoice.getMessage().getContent()) {
                    responseMsg.append(chatCompletionChoice.getMessage().getContent());
                }
            }
        }
        contextInfo.add(new ChatMessage(RoleEnum.ASSISTANT.getRoleName(), responseMsg.toString()));
        log.info("返回消息:{}", responseMsg);
    }

    @Override
    public void createImage(String content) {

        OpenAiService service = new OpenAiService(key, Duration.ofSeconds(30));
        CreateImageRequest request = CreateImageRequest.builder()
                .prompt(content)
                .build();
        System.out.println("\nImage is located at:");
        System.out.println(service.createImage(request).getData().get(0).getUrl());
    }

    private static boolean checkTokenUsage(String message) {
        return message != null && message.contains("This model's maximum context length is");
    }

    private void randomSleep() throws InterruptedException {
        Thread.sleep(500 + RANDOM.nextInt(200));
    }
}

