package br.com.alura.ecomart.chatbot.infra.openai;

import com.theokanning.openai.OpenAiHttpException;
import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.messages.Message;
import com.theokanning.openai.messages.MessageRequest;
import com.theokanning.openai.runs.RunCreateRequest;
import com.theokanning.openai.service.OpenAiService;
import com.theokanning.openai.threads.ThreadRequest;
import io.reactivex.Flowable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

@Component
public class OpenAIClient {

    private final String apiKey;
    private final String model;
    private final String assistantId;
    private final OpenAiService service;
    private String threadId;

    public OpenAIClient(@Value("${app.openai.api.key}") String apiKey, @Value("${app.openai.model}") String model,
                        @Value("${app.openai.assistant.id}") String assistantId) {
        this.apiKey = apiKey;
        this.model = model;
        this.assistantId = assistantId;
        this.service = new OpenAiService(apiKey, Duration.ofSeconds(60));
    }

    public String enviarRequisicaoChatCompletionAssistant(DadosRequisicaoChatCompletion dados) {
        var messageRequest = MessageRequest
                .builder()
                .role(ChatMessageRole.USER.value())
                .content(dados.promptUsuario())
                .build();

        if (this.threadId == null) {
            var threadRequest = ThreadRequest
                    .builder()
                    .messages(Collections.singletonList(messageRequest))
                    .build();

            var thread = service.createThread(threadRequest);
            this.threadId = thread.getId();
        } else {
            service.createMessage(this.threadId, messageRequest);
        }

        var runRequest = RunCreateRequest
                .builder()
                .assistantId(assistantId)
                .build();
        var run = service.createRun(threadId, runRequest);

        try {
            while (!run.getStatus().equalsIgnoreCase("completed")) {
                Thread.sleep(1000L * 10);
                run = service.retrieveRun(threadId, run.getId());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        var mensagens = service.listMessages(threadId);

        return mensagens
                .getData()
                .stream()
                .sorted(Comparator.comparingInt(Message::getCreatedAt).reversed())
                .findFirst().get().getContent().get(0).getText().getValue();
    }

    public Flowable<ChatCompletionChunk> enviarRequisicaoChatCompletion(DadosRequisicaoChatCompletion dados) {
        var request = ChatCompletionRequest
                .builder()
                .model(model)
                .stream(true)
                .messages(Arrays.asList(
                        new ChatMessage(
                                ChatMessageRole.SYSTEM.value(),
                                dados.promptSistema()),
                        new ChatMessage(
                                ChatMessageRole.USER.value(),
                                dados.promptUsuario())))
                .build();

        var segundosParaProximaTentiva = 5;
        var tentativas = 0;
        while (tentativas++ != 5) {
            try {
                return service.streamChatCompletion(request);
            } catch (OpenAiHttpException ex) {
                var errorCode = ex.statusCode;
                switch (errorCode) {
                    case 401 -> throw new RuntimeException("Erro com a chave da API!", ex);
                    case 429, 500, 503 -> {
                        try {
                            Thread.sleep(1000 * segundosParaProximaTentiva);
                            segundosParaProximaTentiva *= 2;
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
        throw new RuntimeException("API Fora do ar! Tentativas finalizadas sem sucesso!");
    }

}
