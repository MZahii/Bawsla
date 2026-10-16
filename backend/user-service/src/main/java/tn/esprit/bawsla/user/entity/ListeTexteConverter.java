package tn.esprit.bawsla.user.entity;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stocke une liste de textes dans une seule colonne, au format JSON (["Data", "Web"]). */
@Converter
public class ListeTexteConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> TYPE = new TypeReference<>() { };

    @Override
    public String convertToDatabaseColumn(List<String> liste) {
        if (liste == null || liste.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(liste);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Liste non sérialisable", e);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String colonne) {
        if (colonne == null || colonne.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(JSON.readValue(colonne, TYPE));
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }
}
