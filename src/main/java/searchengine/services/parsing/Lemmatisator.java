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

    private Language checkLanguage(String word) {

        String rus = "[а-яА-Я]+";
        String eng = "[a-zA-Z]+";

        if (word.matches(rus)) {
            return Language.RUS;
        } else if (word.matches(eng)) {
            return Language.ENG;
        } else {
            return Language.UNIDENTIFIED;
        }
    }

    public Map<String, Integer> collectLemmasAndRanks(String html) {
        Document doc = Jsoup.parse(html);
        String plainText = doc.text();
        String[] words = plainText.toLowerCase().split("[^a-zа-я]+");
        HashMap<String, Integer> lemmas = new HashMap<>();
        for (String word : words) {
            if (isDictionaryWord(word)) {
                List<String> normalForms = checkLanguage(word).equals(Language.RUS) ?
                        russianMorph.getNormalForms(word) : englishMorph.getNormalForms(word);
                String normalWord = normalForms.iterator().next();
                lemmas.put(normalWord, lemmas.containsKey(normalWord) ? (lemmas.get(normalWord) + 1) : 1);
            }
        }
        return lemmas;
    }

    public List<String> convertTextIntoLemmasList(String text) {
        String[] words = text.toLowerCase().split("[^a-zа-я]+");
        HashSet<String> lemmas = new HashSet<>();
        for (String word : words) {
            if (isDictionaryWord(word)) {
                List<String> normalForms = checkLanguage(word).equals(Language.RUS) ?
                        russianMorph.getNormalForms(word) : englishMorph.getNormalForms(word);
                String normalWord = normalForms.iterator().next();
                lemmas.add(normalWord);
            }
        }
        return new ArrayList<>(lemmas);
    }

    public String getWordLemma(String word) {
        String normalWord = null;
        if (isDictionaryWord(word)) {
            List<String> normalForms = checkLanguage(word).equals(Language.RUS) ?
                    russianMorph.getNormalForms(word) : englishMorph.getNormalForms(word);
            normalWord = normalForms.iterator().next();
        }
        return normalWord;
    }

    private boolean isDictionaryWord(String word) {
        return !(word.isBlank() || word.length() < 3 || word.length() > 45 ||
                checkLanguage(word).equals(Language.RUS) && !isCorrectRussianWord(word) ||
                checkLanguage(word).equals(Language.ENG) && !isCorrectEnglishWord(word) ||
                checkLanguage(word).equals(Language.UNIDENTIFIED));
    }
}