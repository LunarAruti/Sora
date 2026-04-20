package ucadmin.tools;

import java.util.Objects;

/**
 * Collection of simple list utilities for tooling and actions.
 * Additional list types will be added here as needed.
 */
public final class Lists {

    private Lists() {}

    /**
     * Doubly-linked list implementation intended for short-lived, temporary data.
     * Node references are independent and are how navigation is performed.
     */
    public static class LinkedList {

        private Node head;
        private Node tail;
        private int size;

        /**
         * Creates an empty linked list with no nodes.
         *
         * Behavior:
         * - Head and tail start as null.
         * - Size starts at 0.
         */
        public LinkedList() {}

        /**
         * Node wrapper holding a value with previous/next references.
         * These references are the only way to navigate the list.
         */
        public static class Node {
            private Object value;
            private Node prev;
            private Node next;

            private Node(Object value) {
                this.value = value;
            }

            /**
             * Returns the value stored in this node.
             *
             * Behavior:
             * - Returns null if the node was cleared after removal.
             *
             * @return the stored value, or null if cleared.
             */
            public Object getValue() {
                return value;
            }

            /**
             * Returns the previous node reference.
             *
             * Behavior:
             * - Returns null if this node is the head or has been removed.
             *
             * @return the previous node, or null.
             */
            public Node getPrev() {
                return prev;
            }

            /**
             * Returns the next node reference.
             *
             * Behavior:
             * - Returns null if this node is the tail or has been removed.
             *
             * @return the next node, or null.
             */
            public Node getNext() {
                return next;
            }
        }

        /**
         * Returns the total number of nodes in the list.
         *
         * Behavior:
         * - Tracks the count of currently linked nodes.
         *
         * @return the current size of the list.
         */
        public int size() {
            return size;
        }

        /**
         * Returns whether the list is empty.
         *
         * Behavior:
         * - True only when size is 0.
         *
         * @return true if the list has no nodes, false otherwise.
         */
        public boolean isEmpty() {
            return size == 0;
        }

        /**
         * Returns the first node in the list.
         *
         * Behavior:
         * - Returns null if the list is empty.
         *
         * @return the head node or null if empty.
         */
        public Node getHead() {
            return head;
        }

        /**
         * Returns the last node in the list.
         *
         * Behavior:
         * - Returns null if the list is empty.
         *
         * @return the tail node or null if empty.
         */
        public Node getTail() {
            return tail;
        }

        /**
         * Reads the node at the given index.
         *
         * Behavior:
         * - Uses zero-based indexing.
         * - Traverses from head or tail based on proximity.
         *
         * @param index zero-based index to read.
         * @return the node at the given index.
         * @throws IndexOutOfBoundsException if index < 0 or index >= size.
         */
        public Node getNode(int index) {
            return nodeAt(index);
        }

        /**
         * Reads the value stored at the given index.
         *
         * Behavior:
         * - Uses zero-based indexing.
         * - Returns the value from the node at the requested index.
         *
         * @param index zero-based index to read.
         * @return the value stored at that index (may be null).
         * @throws IndexOutOfBoundsException if index < 0 or index >= size.
         */
        public Object get(int index) {
            return nodeAt(index).value;
        }

        /**
         * Inserts a new node at the specified index.
         *
         * Usage:
         *   list.add(0, value);      // insert at head
         *   list.add(size, value);   // append at tail
         *
         * Behavior:
         * - Uses zero-based indexing.
         * - Accepts index in range [0, size].
         * - Links the new node between existing neighbors.
         * - Updates head/tail when inserting at boundaries.
         * - Increments list size.
         *
         * Index is zero unless specified (0=head, size=append).
         * @param value value to store in the new node (may be null).
         * @return the newly created node now linked into the list.
         * @throws IndexOutOfBoundsException if index < 0 or index > size.
         */
        public Node add( Object value) {
            return add(0,value);
        }

        /**
         * Inserts a new node at the specified index.
         *
         * Usage:
         *   list.add(0, value);      // insert at head
         *   list.add(size, value);   // append at tail
         *
         * Behavior:
         * - Uses zero-based indexing.
         * - Accepts index in range [0, size].
         * - Links the new node between existing neighbors.
         * - Updates head/tail when inserting at boundaries.
         * - Increments list size.
         *
         * @param index zero-based insert position (0=head, size=append).
         * @param value value to store in the new node (may be null).
         * @return the newly created node now linked into the list.
         * @throws IndexOutOfBoundsException if index < 0 or index > size.
         */
        public Node add(int index, Object value) {
            if (index < 0 || index > size) {
                throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
            }

            Node node = new Node(value);

            if (size == 0) {
                head = node;
                tail = node;
            } else if (index == 0) {
                node.next = head;
                head.prev = node;
                head = node;
            } else if (index == size) {
                node.prev = tail;
                tail.next = node;
                tail = node;
            } else {
                Node current = nodeAt(index);
                Node prev = current.prev;
                node.prev = prev;
                node.next = current;
                prev.next = node;
                current.prev = node;
            }

            size++;
            return node;
        }

        /**
         * Removes the node at the specified index and returns its value.
         *
         * Behavior:
         * - Uses zero-based indexing.
         * - Unlinks the node, reconnecting its neighbors.
         * - Clears the removed node's references and value.
         * - Updates head/tail when removing at boundaries.
         * - Decrements list size.
         *
         * @param index zero-based index to remove.
         * @return the value stored in the removed node (may be null).
         * @throws IndexOutOfBoundsException if index < 0 or index >= size.
         */
        public Object remove(int index) {
            Node target = nodeAt(index);
            Object value = target.value;

            Node prev = target.prev;
            Node next = target.next;

            if (prev != null) {
                prev.next = next;
            } else {
                head = next;
            }

            if (next != null) {
                next.prev = prev;
            } else {
                tail = prev;
            }

            target.prev = null;
            target.next = null;
            target.value = null;
            size--;
            return value;
        }

        private Node nodeAt(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
            }

            if (index < (size / 2)) {
                Node current = head;
                for (int i = 0; i < index; i++) {
                    current = current.next;
                }
                return current;
            }

            Node current = tail;
            for (int i = size - 1; i > index; i--) {
                current = current.prev;
            }
            return current;
        }
    }

    //########################################

    /**
     * Resizing array list with configurable growth and shrink behavior.
     * Uses a private sentinel value to represent empty slots.
     */
    public static class ResizingArray<T> {

        private static final Object EMPTY = new Object();

        private int resizeStep = 10;
        private int shrinkBuffer = 10;
        private int minCapacity = 10;

        private Object[] data;
        private int size;

        /**
         * Creates an empty resizing array with default capacity.
         *
         * Behavior:
         * - Capacity starts at MIN_CAPACITY (or RESIZE_STEP if smaller).
         * - Size starts at 0.
         */
        public ResizingArray() {
            int capacity = AMath.max(minCapacity, resizeStep);
            data = new Object[capacity];
            fillEmpty(0, data.length);
        }

        /**
         * Returns the number of stored elements.
         *
         * Behavior:
         * - Tracks only active elements (not empty slots).
         *
         * @return current element count.
         */
        public int size() {
            return size;
        }

        /**
         * Returns whether the array is empty.
         *
         * Behavior:
         * - True only when size is 0.
         *
         * @return true if empty, false otherwise.
         */
        public boolean isEmpty() {
            return size == 0;
        }

        /**
         * Returns the current growth step size.
         *
         * Behavior:
         * - Used when expanding the backing array.
         * - Must be >= 1.
         *
         * @return the growth step size.
         */
        public int getResizeStep() {
            return resizeStep;
        }

        /**
         * Updates the growth step size.
         *
         * Behavior:
         * - Controls how many slots are added per resize.
         * - Must be >= 1.
         * - Does not immediately resize unless needed by a later add.
         *
         * @param resizeStep new growth step size (>= 1).
         * @throws IllegalArgumentException if resizeStep < 1.
         */
        public void setResizeStep(int resizeStep) {
            if (resizeStep < 1) {
                throw new IllegalArgumentException("resizeStep must be >= 1");
            }
            this.resizeStep = resizeStep;
        }

        /**
         * Returns the current shrink buffer size.
         *
         * Behavior:
         * - The list shrinks only when empty slots exceed this buffer.
         * - Must be >= 0.
         *
         * @return the shrink buffer size.
         */
        public int getShrinkBuffer() {
            return shrinkBuffer;
        }

        /**
         * Updates the shrink buffer size.
         *
         * Behavior:
         * - Larger values reduce shrink frequency.
         * - Must be >= 0.
         * - May trigger a shrink check immediately.
         *
         * @param shrinkBuffer new shrink buffer (>= 0).
         * @throws IllegalArgumentException if shrinkBuffer < 0.
         */
        public void setShrinkBuffer(int shrinkBuffer) {
            if (shrinkBuffer < 0) {
                throw new IllegalArgumentException("shrinkBuffer must be >= 0");
            }
            this.shrinkBuffer = shrinkBuffer;
            shrinkIfNeeded();
        }

        /**
         * Returns the minimum allowed capacity.
         *
         * Behavior:
         * - Backing array will never shrink below this value.
         * - Must be >= 1.
         *
         * @return the minimum capacity.
         */
        public int getMinCapacity() {
            return minCapacity;
        }

        /**
         * Updates the minimum allowed capacity.
         *
         * Behavior:
         * - Must be >= 1.
         * - If current capacity is below the new minimum,
         *   the backing array grows to meet it.
         *
         * @param minCapacity new minimum capacity (>= 1).
         * @throws IllegalArgumentException if minCapacity < 1.
         */
        public void setMinCapacity(int minCapacity) {
            if (minCapacity < 1) {
                throw new IllegalArgumentException("minCapacity must be >= 1");
            }
            this.minCapacity = minCapacity;
            if (data.length < minCapacity) {
                Object[] next = new Object[minCapacity];
                System.arraycopy(data, 0, next, 0, size);
                for (int i = size; i < next.length; i++) {
                    next[i] = EMPTY;
                }
                data = next;
            }
        }

        /**
         * Returns the value stored at the given index.
         *
         * Behavior:
         * - Uses zero-based indexing.
         * - Throws if index is outside [0, size).
         *
         * @param index zero-based index to read.
         * @return the value at that index (may be null).
         * @throws IndexOutOfBoundsException if index < 0 or index >= size.
         */
        @SuppressWarnings("unchecked")
        public T get(int index) {
            checkIndex(index);
            Object value = data[index];
            return value == EMPTY ? null : (T) value;
        }

        /**
         * Inserts a value at the head of the list.
         *
         * Behavior:
         * - Equivalent to add(0, value).
         * - Grows the backing array when full.
         *
         * @param value value to store (may be null).
         */
        public void add(T value) {
            add(0, value);
        }

        /**
         * Inserts a value at the specified index.
         *
         * Usage:
         *   list.add(0, value);      // insert at head
         *   list.add(size, value);   // append at tail
         *
         * Behavior:
         * - Uses zero-based indexing.
         * - Accepts index in range [0, size].
         * - Grows the backing array when full.
         * - Shifts elements right to make space.
         * - Increments size.
         *
         * @param index zero-based insert position (0=head, size=append).
         * @param value value to store (may be null).
         * @throws IndexOutOfBoundsException if index < 0 or index > size.
         */
        public void add(int index, T value) {
            if (index < 0 || index > size) {
                throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
            }
            ensureCapacity(size + 1);

            if (index < size) {
                System.arraycopy(data, index, data, index + 1, size - index);
            }

            data[index] = value;
            size++;
            if (size < data.length) {
                data[size] = EMPTY;
            }
        }

        /**
         * Removes the value at the specified index and returns it.
         *
         * Behavior:
         * - Uses zero-based indexing.
         * - Shifts elements left to close the gap.
         * - Clears the last slot using the EMPTY sentinel.
         * - Shrinks the backing array when enough buffer exists.
         * - Decrements size.
         *
         * @param index zero-based index to remove.
         * @return the removed value (may be null).
         * @throws IndexOutOfBoundsException if index < 0 or index >= size.
         */
        @SuppressWarnings("unchecked")
        public T remove(int index) {
            checkIndex(index);
            Object removed = data[index];

            int moveCount = size - index - 1;
            if (moveCount > 0) {
                System.arraycopy(data, index + 1, data, index, moveCount);
            }

            size--;
            data[size] = EMPTY;
            shrinkIfNeeded();
            return removed == EMPTY ? null : (T) removed;
        }

        /**
         * Finds the index of the first element that equals the target value.
         *
         * Behavior:
         * - Uses zero-based indexing.
         * - Compares values using Object.equals (not reference equality).
         * - Returns -1 when no match is found.
         *
         * @param value value to search for (may be null).
         * @return the index of the first matching value, or -1 if not found.
         */
        public int find(T value) {
            for (int i = 0; i < size; i++) {
                Object current = data[i];
                if (current == EMPTY) {
                    continue;
                }
                if (Objects.equals(value, current)) {
                    return i;
                }
            }
            return -1;
        }

        private void checkIndex(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("index=" + index + ", size=" + size);
            }
        }

        private void ensureCapacity(int requiredSize) {
            if (requiredSize <= data.length) {
                return;
            }
            int newCapacity = data.length;
            while (newCapacity < requiredSize) {
                newCapacity += AMath.max(1, resizeStep);
            }
            Object[] next = new Object[newCapacity];
            System.arraycopy(data, 0, next, 0, size);
            for (int i = size; i < next.length; i++) {
                next[i] = EMPTY;
            }
            data = next;
        }

        private void shrinkIfNeeded() {
            int buffer = data.length - size;
            if (buffer <= shrinkBuffer) {
                return;
            }
            int target = data.length - AMath.max(1, resizeStep);
            int min = AMath.max(minCapacity, resizeStep);
            if (target < min || target < size) {
                return;
            }
            Object[] next = new Object[target];
            System.arraycopy(data, 0, next, 0, size);
            for (int i = size; i < next.length; i++) {
                next[i] = EMPTY;
            }
            data = next;
        }

        private void fillEmpty(int start, int end) {
            for (int i = start; i < end; i++) {
                data[i] = EMPTY;
            }
        }
    }
}
