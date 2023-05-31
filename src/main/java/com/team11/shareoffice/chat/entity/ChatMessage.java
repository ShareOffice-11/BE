package com.team11.shareoffice.chat.entity;

import com.team11.shareoffice.member.entity.Member;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;


@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Member sender;

    @Column(nullable = false)
    private String content;

    @ManyToOne
    private ChatRoom room;

    public ChatMessage(Member sender, String content, ChatRoom room) {
        this.sender = sender;
        this.content = content;
        this.room = room;
    }
}