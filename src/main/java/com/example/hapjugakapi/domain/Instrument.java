package com.example.hapjugakapi.domain;

public enum Instrument {

    VOCAL("보컬"),
    ACOUSTIC("통기타"),
    GUITAR("일렉기타"),
    BASS("베이스기타"),
    DRUM("드럼"),
    KEY("건반");

    private final String label;

    Instrument(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Instrument fromLabel(String label) {
        for (Instrument i : values()) {
            if (i.label.equals(label)) return i;
        }
        throw new IllegalArgumentException("알 수 없는 악기: " + label);
    }
}