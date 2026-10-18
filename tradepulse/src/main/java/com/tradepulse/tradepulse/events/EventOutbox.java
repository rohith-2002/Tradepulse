package com.tradepulse.tradepulse.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventOutbox {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void enqueue(String topic,String key,Object event){
        try{jdbc.update("INSERT INTO event_outbox(topic,event_key,event_type,payload) VALUES (?,?,?,?)",topic,key,event.getClass().getSimpleName(),json.writeValueAsString(event));}
        catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalArgumentException("Cannot serialize event",ex);}
    }
}
