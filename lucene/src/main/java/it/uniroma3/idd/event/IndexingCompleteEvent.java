package it.uniroma3.idd.event;

import org.springframework.context.ApplicationEvent;

public class IndexingCompleteEvent extends ApplicationEvent {
    public IndexingCompleteEvent(Object source) {
        super(source);
    }
}
