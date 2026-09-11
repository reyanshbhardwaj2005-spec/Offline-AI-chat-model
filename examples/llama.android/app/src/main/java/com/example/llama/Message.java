package com.example.llama;

public class Message {

    public static final int USER = 0;
    public static final int ASSISTANT = 1;

    private String content;
    private final int type;

    public Message(String content, int type) {
        this.content = content;
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public int getType() {
        return type;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
