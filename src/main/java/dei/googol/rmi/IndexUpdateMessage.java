package dei.googol.rmi;

import java.io.Serializable;

public class IndexUpdateMessage implements Serializable {
    private String word;
    private String url;
    private String title;
    private String snippet;

    public IndexUpdateMessage(String word, String url, String title, String snippet) {
        this.word = word;
        this.url = url;
        this.title = title;
        this.snippet = snippet;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() { return title; }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSnippet() { return snippet; }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }


    @Override
    public String toString() {
        return "IndexUpdateMessage{" +
                "word='" + word + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
