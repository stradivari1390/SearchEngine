package searchengine.services.lemmatisationService;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class Lemmatisator {

    private final String[] redundantForms = {"ПРЕДЛ", "СОЮЗ", "МЕЖД", "ВВОДН", "ЧАСТ", "МС", "CONJ", "PART"};
    private final LuceneMorphology russianMorph;
    private final LuceneMorphology englishMorph;

    public Lemmatisator() {

        try {
            russianMorph = new RussianLuceneMorphology();
            englishMorph = new EnglishLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
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
            return "Russian";
        } else if (word.matches(eng)) {
            return "English";
        } else {
            return "Unidentified";
        }
    }

    public Map<String, Integer> collectLemmasAndRanks(String text) {

        String[] words = text.toLowerCase().split("[^a-zа-я]+");
        HashMap<String, Integer> lemmas = new HashMap<>();

        for (String word : words) {

            if (word.isBlank() || word.length() < 3) {
                continue;
            }
            List<String> normalForms = null;

            if(checkLanguage(word).equals("Russian") && isCorrectRussianWord(word)) {
                normalForms = russianMorph.getNormalForms(word);
            }

            if(checkLanguage(word).equals("English") && isCorrectEnglishWord(word)) {
                normalForms = englishMorph.getNormalForms(word);
            }

            if (normalForms == null) {
                continue;
            }

            String normalWord = normalForms.get(0);

            if (lemmas.containsKey(normalWord)) {
                lemmas.put(normalWord, lemmas.get(normalWord) + 1);
            } else {
                lemmas.put(normalWord, 1);
            }
        }
        return lemmas;
    }
}