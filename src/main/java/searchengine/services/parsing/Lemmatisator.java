package searchengine.services.parsing;

import lombok.SneakyThrows;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import searchengine.exceptions.LemmatizationException;

import java.io.IOException;
import java.util.*;

@Component
public class Lemmatisator {

    private static final String[] redundantForms = {"ПРЕДЛ", "СОЮЗ", "МЕЖД", "ВВОДН", "ЧАСТ", "МС", "CONJ", "PART"};
    private final LuceneMorphology russianMorph;
    private final LuceneMorphology englishMorph;

    @SneakyThrows
    public Lemmatisator() {
        try {
            russianMorph = new RussianLuceneMorphology();
            englishMorph = new EnglishLuceneMorphology();
        } catch (IOException e) {
            throw new LemmatizationException("An error occurred while initializing the Lemmatisator", e);
        }
    }

    private boolean isCorrectRussianWord(String word) {

        List<String> wordBaseFormList = russianMorph.getMorphInfo(word);

        for (String value : redundantForms) {
            if (wordBaseFormList.toString().toUpperCase().contains(value)) {
                return false;
            }
        }
        return true;
    }

    private boolean isCorrectEnglishWord(String word) {

        List<String> wordBaseFormList = englishMorph.getMorphInfo(word);

        for (String value : redundantForms) {
            if (wordBaseFormList.toString().toUpperCase().contains(value)) {
                return false;
            }
        }
        return true;
    }

    private String checkLanguage(String word) {

        String rus = "[а-яА-Я]+";
        String eng = "[a-zA-Z]+";

        if (word.matches(rus)) {
            return "Rus";
        } else if (word.matches(eng)) {
            return "Eng";
        } else {
            return "Unidentified";
        }
    }

    public Map<String, Integer> collectLemmasAndRanks(String html) {
        Document doc = Jsoup.parse(html);
        String plainText = doc.text();
        String[] words = plainText.toLowerCase().split("[^a-zа-я]+");
        HashMap<String, Integer> lemmas = new HashMap<>();

        for (String word : words) {
            if (word.isBlank() || word.length() < 3 || word.length() > 45 ||
                    checkLanguage(word).equals("Rus") && !isCorrectRussianWord(word) ||
                    checkLanguage(word).equals("Eng") && !isCorrectEnglishWord(word) ||
                    checkLanguage(word).equals("Unidentified")) {
                continue;
            }
            List<String> normalForms = checkLanguage(word).equals("Rus") ?
                    russianMorph.getNormalForms(word) : englishMorph.getNormalForms(word);
            String normalWord = normalForms.get(0);
            lemmas.put(normalWord, lemmas.containsKey(normalWord) ? (lemmas.get(normalWord) + 1) : 1);
        }
        return lemmas;
    }
}