import java.io.File;
import java.util.HashMap;
import java.util.Map;

// Time complexity O(1) for both put and get
// Space complexity O(capacity)
public class LRUCache {
    class DLinkedNode {
        String key;
        File value;
        DLinkedNode prev;
        DLinkedNode next;
    }

    private Map<String, DLinkedNode> cache = new HashMap<>();
    private int size;
    private int capacity;
    private DLinkedNode head, tail;

    // helper function to debug
    public void iterate() {
        for(Map.Entry<String, DLinkedNode> entry: this.cache.entrySet()) {
            System.out.println((entry.getKey()));
        }
    }

    private synchronized void addNode(DLinkedNode node) {
        /**
         * Always add the new node right after head.
         */
        node.prev = head;
        node.next = head.next;

        head.next.prev = node;
        head.next = node;
    }

    private synchronized DLinkedNode popTail() {
        /**
         * Pop the current tail.
         */
        DLinkedNode res = tail.prev;
        removeNode(res);
        return res;
    }

    private synchronized void removeNode(DLinkedNode node){
        /**
         * Remove an existing node from the linked list.
         */
        DLinkedNode prev = node.prev;
        DLinkedNode next = node.next;

        prev.next = next;
        next.prev = prev;
    }

    public LRUCache(int capacity) {
        this.size = 0;
        this.capacity = capacity;

        head = new DLinkedNode();
        // head.prev = null;

        tail = new DLinkedNode();
        // tail.next = null;

        head.next = tail;
        tail.prev = head;
    }

    private synchronized void moveToHead(DLinkedNode node){
        /**
         * Move certain node in between to the head.
         */
        removeNode(node);
        addNode(node);
    }

    public synchronized File get(String key) {
        DLinkedNode node = cache.get(key);
        if (node == null) return null;

        // move the accessed node to the head;
        moveToHead(node);
        System.out.println(key + " " + node.value.getName());

        return node.value;
    }

    public synchronized void put(String key, File value) {
        DLinkedNode node = cache.get(key);

        if(node == null) {
            DLinkedNode newNode = new DLinkedNode();
            newNode.key = key;
            newNode.value = value;

            cache.put(key, newNode);
            addNode(newNode);

            ++size;

            if(size > capacity) {
                // pop the tail
                DLinkedNode tail = popTail();
                cache.remove(tail.key);
                --size;
            }
        } else {
            // update the value.
            node.value = value;
            moveToHead(node);
        }
    }
}
