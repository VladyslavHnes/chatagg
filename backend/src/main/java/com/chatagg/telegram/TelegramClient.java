package com.chatagg.telegram;

import com.chatagg.config.AppConfig;
import it.tdlight.client.*;
import it.tdlight.jni.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);
    private static final int BATCH_SIZE = 100;
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {1000, 2000, 4000};

    public enum AuthState {
        NONE, WAITING_CODE, WAITING_PASSWORD, READY, ERROR
    }

    private final AppConfig config;
    private SimpleTelegramClient client;
    private SimpleTelegramClientFactory clientFactory;
    private volatile AuthState authState = AuthState.NONE;
    private volatile CompletableFuture<String> pendingInput;

    public TelegramClient(AppConfig config) {
        this.config = config;
    }

    public AuthState getAuthState() {
        return authState;
    }

    public void submitAuthInput(String input) {
        CompletableFuture<String> future = this.pendingInput;
        if (future != null && !future.isDone()) {
            future.complete(input);
        }
    }

    public void start() throws Exception {
        if (client != null) {
            if (authState == AuthState.READY) {
                return; // Already started and ready
            }
            if (authState == AuthState.WAITING_CODE || authState == AuthState.WAITING_PASSWORD) {
                return; // Waiting for user input, don't restart
            }
        }
        TDLibSettings settings = TDLibSettings.create(
                new APIToken(config.telegramApiId, config.telegramApiHash)
        );

        Path sessionDir = Path.of("tdlib-session");
        Files.createDirectories(sessionDir);
        settings.setDatabaseDirectoryPath(sessionDir.resolve("db"));
        settings.setDownloadedFilesDirectoryPath(sessionDir.resolve("downloads"));

        this.clientFactory = new SimpleTelegramClientFactory();
        SimpleTelegramClientBuilder builder = clientFactory.builder(settings);

        // Completes with the auth outcome: READY, WAITING_CODE, or WAITING_PASSWORD
        CompletableFuture<AuthState> authEventFuture = new CompletableFuture<>();

        builder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, update -> {
            TdApi.AuthorizationState state = update.authorizationState;
            if (state instanceof TdApi.AuthorizationStateReady) {
                log.info("Telegram client authorized and ready");
                authState = AuthState.READY;
                authEventFuture.complete(AuthState.READY);
            } else if (state instanceof TdApi.AuthorizationStateClosed) {
                log.info("Telegram client closed");
                authState = AuthState.NONE;
            }
        });

        builder.setClientInteraction(new ClientInteraction() {
            @Override
            public CompletableFuture<String> onParameterRequest(InputParameter parameter, ParameterInfo parameterInfo) {
                switch (parameter) {
                    case ASK_CODE -> {
                        log.info("Telegram is requesting an authentication code");
                        authState = AuthState.WAITING_CODE;
                        authEventFuture.complete(AuthState.WAITING_CODE);
                        pendingInput = new CompletableFuture<>();
                        return pendingInput;
                    }
                    case ASK_PASSWORD -> {
                        log.info("Telegram is requesting a 2FA password");
                        authState = AuthState.WAITING_PASSWORD;
                        authEventFuture.complete(AuthState.WAITING_PASSWORD);
                        pendingInput = new CompletableFuture<>();
                        return pendingInput;
                    }
                    case TERMS_OF_SERVICE -> {
                        log.info("Accepting Telegram Terms of Service");
                        return CompletableFuture.completedFuture("");
                    }
                    case NOTIFY_LINK -> {
                        if (parameterInfo instanceof ParameterInfoNotifyLink linkInfo) {
                            log.info("Telegram notify link: {}", linkInfo.getLink());
                        }
                        return CompletableFuture.completedFuture("");
                    }
                    default -> {
                        log.warn("Unhandled parameter request: {}", parameter);
                        return CompletableFuture.completedFuture("");
                    }
                }
            }
        });

        this.client = builder.build(
                AuthenticationSupplier.user(config.telegramPhone)
        );

        // Wait up to 30s for auth outcome — completed by either the update handler (READY)
        // or by onParameterRequest (WAITING_CODE / WAITING_PASSWORD).
        AuthState result = authEventFuture.get(30, TimeUnit.SECONDS);
        if (result == AuthState.WAITING_CODE || result == AuthState.WAITING_PASSWORD) {
            log.info("Telegram client is waiting for auth input (state={})", result);
            throw new AuthRequiredException(result);
        }
    }

    public static class AuthRequiredException extends Exception {
        private final AuthState authState;
        public AuthRequiredException(AuthState authState) {
            super("Telegram authentication required: " + authState);
            this.authState = authState;
        }
        public AuthState getAuthState() { return authState; }
    }

    public List<TdApi.Message> getChatHistory(long chatId, long fromMessageId, int limit)
            throws ExecutionException, InterruptedException, TimeoutException {
        return withRetry("getChatHistory", () -> {
            TdApi.GetChatHistory request = new TdApi.GetChatHistory();
            request.chatId = chatId;
            request.fromMessageId = fromMessageId;
            request.offset = 0;
            request.limit = Math.min(limit, BATCH_SIZE);
            request.onlyLocal = false;

            CompletableFuture<TdApi.Messages> future = new CompletableFuture<>();
            client.send(request, result -> {
                if (result.isError()) {
                    future.completeExceptionally(new RuntimeException(
                            "TDLib error: " + result.getError().message));
                } else {
                    future.complete(result.get());
                }
            });

            TdApi.Messages messages = future.get(30, TimeUnit.SECONDS);
            List<TdApi.Message> result = new ArrayList<>();
            if (messages.messages != null) {
                for (TdApi.Message msg : messages.messages) {
                    result.add(msg);
                }
            }
            return result;
        });
    }

    /**
     * Fetches messages newer than the given message ID by paging backward from
     * the newest message until we reach stopAtMessageId.
     */
    public List<TdApi.Message> getMessagesSince(long chatId, long stopAtMessageId)
            throws ExecutionException, InterruptedException, TimeoutException {
        openChat(chatId);

        List<TdApi.Message> allMessages = new ArrayList<>();
        long fromMessageId = 0;

        while (true) {
            List<TdApi.Message> batch = getChatHistory(chatId, fromMessageId, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }

            boolean reachedStop = false;
            for (TdApi.Message msg : batch) {
                if (msg.id <= stopAtMessageId) {
                    reachedStop = true;
                    break;
                }
                allMessages.add(msg);
            }

            if (reachedStop) {
                break;
            }

            fromMessageId = batch.get(batch.size() - 1).id;
            log.info("Fetched {} new messages so far...", allMessages.size());
        }

        return allMessages;
    }

    /**
     * Opens a chat in TDLib and waits for messages to become available.
     * TDLib needs time to download history from the server after opening a chat.
     */
    public void openChat(long chatId)
            throws ExecutionException, InterruptedException, TimeoutException {
        withRetry("openChat(" + chatId + ")", () -> {
            CompletableFuture<TdApi.Ok> future = new CompletableFuture<>();
            client.send(new TdApi.OpenChat(chatId), result -> {
                if (result.isError()) {
                    future.completeExceptionally(new RuntimeException(
                            "TDLib openChat error: " + result.getError().message));
                } else {
                    future.complete(result.get());
                }
            });
            future.get(10, TimeUnit.SECONDS);
            return null;
        });
        log.info("Opened chat {}", chatId);

        // Wait for TDLib to load messages from the server
        for (int attempt = 1; attempt <= 5; attempt++) {
            Thread.sleep(2000);
            List<TdApi.Message> probe = getChatHistory(chatId, 0, 1);
            if (!probe.isEmpty()) {
                log.info("Chat {} has messages available after {}s", chatId, attempt * 2);
                return;
            }
            log.info("Waiting for chat {} messages to load (attempt {}/5)...", chatId, attempt);
        }
        log.warn("Chat {} still has no messages after 10s wait", chatId);
    }

    public List<TdApi.Message> getFullChatHistory(long chatId)
            throws ExecutionException, InterruptedException, TimeoutException {
        openChat(chatId);

        List<TdApi.Message> allMessages = new ArrayList<>();
        long fromMessageId = 0;

        while (true) {
            List<TdApi.Message> batch = getChatHistory(chatId, fromMessageId, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }
            allMessages.addAll(batch);
            fromMessageId = batch.get(batch.size() - 1).id;
            log.info("Fetched {} messages so far...", allMessages.size());
        }

        return allMessages;
    }

    public byte[] downloadFile(int fileId)
            throws ExecutionException, InterruptedException, TimeoutException {
        return withRetry("downloadFile(" + fileId + ")", () -> {
            TdApi.DownloadFile request = new TdApi.DownloadFile();
            request.fileId = fileId;
            request.priority = 1;
            request.synchronous = true;

            CompletableFuture<TdApi.File> future = new CompletableFuture<>();
            client.send(request, result -> {
                if (result.isError()) {
                    future.completeExceptionally(new RuntimeException(
                            "TDLib download error: " + result.getError().message));
                } else {
                    future.complete(result.get());
                }
            });

            TdApi.File file = future.get(60, TimeUnit.SECONDS);
            if (file.local != null && file.local.isDownloadingCompleted) {
                try {
                    return Files.readAllBytes(Path.of(file.local.path));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read downloaded file: " + file.local.path, e);
                }
            }

            throw new RuntimeException("File download did not complete for fileId: " + fileId);
        });
    }

    private <T> T withRetry(String operation, RetryableOperation<T> op)
            throws ExecutionException, InterruptedException, TimeoutException {
        Exception lastException = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return op.execute();
            } catch (Exception e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long delay = RETRY_DELAYS_MS[attempt];
                    log.warn("{} failed (attempt {}/{}), retrying in {}ms: {}",
                            operation, attempt + 1, MAX_RETRIES + 1, delay, e.getMessage());
                    Thread.sleep(delay);
                }
            }
        }
        log.error("{} failed after {} attempts", operation, MAX_RETRIES + 1, lastException);
        if (lastException instanceof ExecutionException ex) throw ex;
        if (lastException instanceof TimeoutException ex) throw ex;
        if (lastException instanceof InterruptedException ex) throw ex;
        throw new RuntimeException(operation + " failed after retries", lastException);
    }

    @FunctionalInterface
    private interface RetryableOperation<T> {
        T execute() throws Exception;
    }

    public void stop() {
        if (client != null) {
            client.sendClose();
        }
        if (clientFactory != null) {
            clientFactory.close();
        }
    }
}
