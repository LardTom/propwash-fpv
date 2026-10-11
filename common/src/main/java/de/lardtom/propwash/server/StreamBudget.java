package de.lardtom.propwash.server;

public final class StreamBudget {
    public static final int MIN_VIEW_DISTANCE = 2;
    public static final int MAX_VIEW_DISTANCE = 32;
    public static final int TICKET_MARGIN = 2;

    private StreamBudget() {
    }

    public static int viewTicketRadius(int viewDistance) {
        return viewDistance + TICKET_MARGIN;
    }

    public static int cost(int viewDistance) {
        int side = 2 * viewTicketRadius(viewDistance) + 1;
        return side * side;
    }

    public static int wantedViewDistance(int requestedViewDistance, int serverViewDistance, int configuredViewDistance) {
        int server = clamp(serverViewDistance, MIN_VIEW_DISTANCE, MAX_VIEW_DISTANCE);
        int player = clamp(requestedViewDistance, MIN_VIEW_DISTANCE, server);
        return clamp(Math.min(player, configuredViewDistance), MIN_VIEW_DISTANCE, MAX_VIEW_DISTANCE);
    }

    public static int[] allocate(int[] wanted, int budget) {
        int n = wanted.length;
        int[] result = new int[n];
        long used = 0;
        for (int i = 0; i < n; i++) {
            result[i] = MIN_VIEW_DISTANCE;
            used += cost(MIN_VIEW_DISTANCE);
        }
        long remaining = budget - used;
        while (remaining > 0) {
            int pick = -1;
            for (int i = 0; i < n; i++) {
                if (result[i] < clamp(wanted[i], MIN_VIEW_DISTANCE, MAX_VIEW_DISTANCE) && (pick < 0 || result[i] < result[pick])) {
                    pick = i;
                }
            }
            if (pick < 0) {
                break;
            }
            long extra = cost(result[pick] + 1) - cost(result[pick]);
            if (extra > remaining) {
                break;
            }
            result[pick]++;
            remaining -= extra;
        }
        return result;
    }

    public static long totalCost(int[] viewDistances) {
        long total = 0;
        for (int d : viewDistances) {
            total += cost(d);
        }
        return total;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
