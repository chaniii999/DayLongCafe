package org.daylongevent.daylongcafe.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@ToString
@Builder
@Setter
public class User {
    private String backNumber;
    private int cupsPurchased;
    private int rank;
}

