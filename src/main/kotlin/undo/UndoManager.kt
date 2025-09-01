class UndoManager<T>(private val capacity: Int) {
    private val stack = ArrayDeque<T>()
    fun push(state: T) {
        if (stack.size == capacity) stack.removeFirst()
        stack.addLast(state)
    }
    fun pop(): T? = if (stack.isEmpty()) null else stack.removeLast()
    fun clear() = stack.clear()
    fun canUndo() = stack.isNotEmpty()
}