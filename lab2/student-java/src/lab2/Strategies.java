package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {

    private Strategies() {
    }

    /**
     * Всегда первый поток из enabled.
     */
    public static final class FirstAlive implements Strategy {

        public int pick(List<Integer> enabled, int last) {
            return enabled.getFirst();
        }
    }

    /**
     * Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1).
     */
    public static final class Replay implements Strategy {

        private final String trace;
        private int pos;

        public Replay(String trace) {
            this.trace = trace;
            pos = 0;
        }

        /**
         * Сбросить позицию в трассе перед прогоном.
         */
        public void beginRun() {
            pos = 0;
        }

        public int pick(List<Integer> enabled, int last) {
            if (pos < trace.length()) {
                return trace.charAt(pos++) - 'A';
            } else {
                throw new RuntimeException("Replay trace has ended");
            }
        }
    }

    /**
     * Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed).
     */
    public static final class RandomPick implements Strategy {

        private final Random random;

        public RandomPick(long seed) {
            random = new Random(seed);
        }

        public int pick(List<Integer> enabled, int last) {
            return enabled.get(random.nextInt(enabled.size()));
        }
    }

    /**
     * Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     * bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE.
     */
    public static final class Dfs implements Strategy {

        private final List<Integer> decisions;
        private final int bound;
        private int remainingPreempt;
        private int pos;

        public Dfs(int bound) {
            this.decisions = new ArrayList<>();
            this.bound = bound;
        }

        /**
         * Подготовиться к новому прогону (позиция в пути, счётчик вытеснений).
         */
        public void beginRun() {
            pos = 0;
            remainingPreempt = bound;
        }

        public int pick(List<Integer> enabled, int last) {
            boolean canContinue = last != -1 && enabled.contains(last);
            List<Integer> options = (canContinue && remainingPreempt == 0) ? List.of(last) : enabled;

            int index = pos < decisions.size() ? options.size() - 1 - decisions.get(pos) : 0;
            if (pos++ >= decisions.size()) {
                decisions.add(options.size() - 1);
            }
            int cur = options.get(index);
            if (canContinue && cur != last) {
                --remainingPreempt;
            }

            return cur;
        }

        /**
         * Выбрать следующий путь; false — дерево обойдено.
         */
        public boolean endRun() {
            for (int i = decisions.size() - 1; i >= 0 && decisions.get(i) == 0; --i) {
                decisions.remove(i);
            }
            if (decisions.isEmpty()) {
                return false;
            } else {
                decisions.set(decisions.size() - 1, decisions.getLast() - 1);
                return true;
            }
        }
    }
}
