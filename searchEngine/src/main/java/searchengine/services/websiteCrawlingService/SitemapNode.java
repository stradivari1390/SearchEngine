package searchengine.services.websiteCrawlingService;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Synchronized;

import java.util.concurrent.CopyOnWriteArrayList;

@EqualsAndHashCode
public class SitemapNode {

    @Getter
    private final String url;

    private SitemapNode parent;

    @Getter
    private CopyOnWriteArrayList<SitemapNode> children;

    private int depth;

    public SitemapNode(String url) {
        this.url = url;
        parent = null;
        children = new CopyOnWriteArrayList<>();
        depth = 0;
    }

    @Synchronized
    public void addChild(SitemapNode node) {
        SitemapNode root = getRootNode();
        if(!root.containsElementInBranch(node.getUrl())) {
            node.setParent(this);
            children.add(node);
        }
    }

    private boolean containsElementInBranch(String url) {
        if (this.url.equals(url)) {
            return true;
        }
        for (SitemapNode child : children) {
            if(child.containsElementInBranch(url))
                return true;
        }
        return false;
    }

    private int getDepth() {
        int result = 0;
        if (parent == null) {
            return result;
        }
        result = parent.getDepth() + 1;
        return result;
    }

    private void setParent(SitemapNode sitemapNode) {
        synchronized (this) {
            this.parent = sitemapNode;
            this.depth = getDepth();
        }
    }

    public SitemapNode getRootNode() {
        return parent == null ? this : parent.getRootNode();
    }

    @Override
    public String toString() {
        return "url = " + url + ", depth = " + depth;
    }
}