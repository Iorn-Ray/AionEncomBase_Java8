/**
 * This file is part of Aion-Lightning <aion-lightning.org>.
 *
 * Aion-Lightning is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * Aion-Lightning is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details. *
 *
 * You should have received a copy of the GNU General Public License along with Aion-Lightning. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 * Credits goes to all Open Source Core Developer Groups listed below Please do not change here something, ragarding the developer credits, except the
 * "developed by XXXX". Even if you edit a lot of files in this source, you still have no rights to call it as "your Core". Everybody knows that this
 * Emulator Core was developed by Aion Lightning
 * 
 * @-Aion-Unique-
 * @-Aion-Lightning
 * @Aion-Engine
 * @Aion-Extreme
 * @Aion-NextGen
 * @Aion-Core Dev.
 */
package com.aionemu.commons.utils.concurrent;

import java.io.PrintStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 运行时统计管理器，用于收集和分析方法执行时间统计信息
 * Runtime statistics manager for collecting and analyzing method execution time statistics
 * 
 * @author NB4L1
 */
@SuppressWarnings("unchecked")
public final class RunnableStatsManager {
    
    private static final Logger log = LoggerFactory.getLogger(RunnableStatsManager.class);
    
    // 类统计信息映射表 Class statistics mapping
    private static final Map<Class<?>, ClassStat> classStats = new HashMap<Class<?>, ClassStat>();
    
    /**
     * 类统计信息，记录类中方法的执行统计
     * Class statistics for recording method execution statistics
     */
    private static final class ClassStat {
        private final String className;
        private final MethodStat runnableStat;
        
        private String[] methodNames = new String[0];
        private MethodStat[] methodStats = new MethodStat[0];
        
        private ClassStat(Class<?> clazz) {
            className = clazz.getName().replace("com.aionemu.gameserver.", "");
            runnableStat = new MethodStat(className, "run()");
            
            methodNames = new String[] {"run()"};
            methodStats = new MethodStat[] {runnableStat};
            
            classStats.put(clazz, this);
        }
        
        private MethodStat getRunnableStat() {
            return runnableStat;
        }
        
        /**
         * 获取方法统计信息
         * Get method statistics
         */
        private MethodStat getMethodStat(String methodName, boolean synchronizedAlready) {
            if ("run()".equals(methodName)) {
                return runnableStat;
            }
            
            for (int i = 0; i < methodNames.length; i++) {
                if (methodNames[i].equals(methodName)) {
                    return methodStats[i];
                }
            }
            
            if (!synchronizedAlready) {
                synchronized (this) {
                    return getMethodStat(methodName, true);
                }
            }
            
            methodName = methodName.intern();
            
            final MethodStat methodStat = new MethodStat(className, methodName);
            
            methodNames = (String[]) ArrayUtils.add(methodNames, methodName);
            methodStats = (MethodStat[]) ArrayUtils.add(methodStats, methodStat);
            
            return methodStat;
        }
    }
    
    /**
     * 方法统计信息，记录单个方法的执行统计
     * Method statistics for recording single method execution statistics
     */
    private static final class MethodStat {
        private final ReentrantLock lock = new ReentrantLock();
        
        private final String className;
        private final String methodName;
        
        private long count;      // 执行次数 Execution count
        private long total;      // 总执行时间 Total execution time
        private long min = Long.MAX_VALUE;  // 最小执行时间 Minimum execution time
        private long max = Long.MIN_VALUE;  // 最大执行时间 Maximum execution time
        
        private MethodStat(String className, String methodName) {
            this.className = className;
            this.methodName = methodName;
        }
        
        /**
         * 处理统计信息
         * Handle statistics
         */
        private void handleStats(long runTime) {
            lock.lock();
            try {
                count++;
                total += runTime;
                min = Math.min(min, runTime);
                max = Math.max(max, runTime);
            } finally {
                lock.unlock();
            }
        }
    }
    
    /**
     * 获取类统计信息
     * Get class statistics
     */
    private static ClassStat getClassStat(Class<?> clazz, boolean synchronizedAlready) {
        ClassStat classStat = classStats.get(clazz);
        
        if (classStat != null) {
            return classStat;
        }
        
        if (!synchronizedAlready) {
            synchronized (RunnableStatsManager.class) {
                return getClassStat(clazz, true);
            }
        }
        
        return new ClassStat(clazz);
    }
    
    /**
     * 处理Runnable类的统计信息
     * Handle statistics for Runnable class
     */
    public static void handleStats(Class<? extends Runnable> clazz, long runTime) {
        getClassStat(clazz, false).getRunnableStat().handleStats(runTime);
    }
    
    /**
     * 处理指定方法的统计信息
     * Handle statistics for specified method
     */
    public static void handleStats(Class<?> clazz, String methodName, long runTime) {
        getClassStat(clazz, false).getMethodStat(methodName, false).handleStats(runTime);
    }
    
    /**
     * 统计信息排序方式枚举
     * Statistics sorting method enumeration
     */
    public static enum SortBy {
        AVG("average"),    // 平均时间 Average time
        COUNT("count"),    // 执行次数 Execution count
        TOTAL("total"),   // 总时间 Total time
        NAME("class"),    // 类名 Class name
        METHOD("method"), // 方法名 Method name
        MIN("min"),      // 最小时间 Minimum time
        MAX("max");      // 最大时间 Maximum time
        
        private final String xmlAttributeName;
        
        private SortBy(String xmlAttributeName) {
            this.xmlAttributeName = xmlAttributeName;
        }
        
        private final Comparator<MethodStat> comparator = new Comparator<MethodStat>() {
            @Override
            @SuppressWarnings("rawtypes")
            public int compare(MethodStat o1, MethodStat o2) {
                final Comparable c1 = getComparableValueOf(o1);
                final Comparable c2 = getComparableValueOf(o2);
                
                if (c1 instanceof Number) {
                    return c2.compareTo(c1);
                }
                
                final String s1 = (String) c1;
                final String s2 = (String) c2;
                
                final int len1 = s1.length();
                final int len2 = s2.length();
                final int n = Math.min(len1, len2);
                
                for (int k = 0; k < n; k++) {
                    char ch1 = s1.charAt(k);
                    char ch2 = s2.charAt(k);
                    
                    if (ch1 != ch2) {
                        if (Character.isUpperCase(ch1) != Character.isUpperCase(ch2)) {
                            return ch2 - ch1;
                        } else {
                            return ch1 - ch2;
                        }
                    }
                }
                
                final int result = len1 - len2;
                
                if (result != 0) {
                    return result;
                }
                
                switch (SortBy.this) {
                    case METHOD:
                        return NAME.comparator.compare(o1, o2);
                    default:
                        return 0;
                }
            }
        };
        
        @SuppressWarnings("rawtypes")
        private Comparable getComparableValueOf(MethodStat stat) {
            switch (this) {
                case AVG:
                    return stat.total / stat.count;
                case COUNT:
                    return stat.count;
                case TOTAL:
                    return stat.total;
                case NAME:
                    return stat.className;
                case METHOD:
                    return stat.methodName;
                case MIN:
                    return stat.min;
                case MAX:
                    return stat.max;
                default:
                    throw new InternalError();
            }
        }
        
        private static final SortBy[] VALUES = SortBy.values();
    }
    
    /**
     * 导出类统计信息
     * Export class statistics
     */
    public static void dumpClassStats() {
        dumpClassStats(null);
    }
    
    /**
     * 导出类统计信息，支持指定排序方式
     * Export class statistics with specified sorting method
     */
    public static void dumpClassStats(final SortBy sortBy) {
        final List<MethodStat> methodStats = new ArrayList<MethodStat>();
        
        synchronized (RunnableStatsManager.class) {
            for (ClassStat classStat : classStats.values()) {
                for (MethodStat methodStat : classStat.methodStats) {
                    if (methodStat.count > 0) {
                        methodStats.add(methodStat);
                    }
                }
            }
        }
        
        if (sortBy != null) {
            Collections.sort(methodStats, sortBy.comparator);
        }
        
        final List<String> lines = new ArrayList<String>();
        
        lines.add("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>");
        lines.add("<entries>");
        lines.add("\t<!-- This XML contains statistics about execution times. -->");
        lines.add("\t<!-- Submitted results will help the developers to optimize the server. -->");
        
        final String[][] values = new String[SortBy.VALUES.length][methodStats.size()];
        final int[] maxLength = new int[SortBy.VALUES.length];
        
        for (int i = 0; i < SortBy.VALUES.length; i++) {
            final SortBy sort = SortBy.VALUES[i];
            
            for (int k = 0; k < methodStats.size(); k++) {
                @SuppressWarnings("rawtypes")
                final Comparable c = sort.getComparableValueOf(methodStats.get(k));
                
                final String value;
                
                if (c instanceof Number) {
                    value = NumberFormat.getInstance(Locale.ENGLISH).format(((Number) c).longValue());
                } else {
                    value = String.valueOf(c);
                }
                
                values[i][k] = value;
                
                maxLength[i] = Math.max(maxLength[i], value.length());
            }
        }
        
        for (int k = 0; k < methodStats.size(); k++) {
            StringBuilder sb = new StringBuilder();
            sb.append("\t<entry ");
            
            EnumSet<SortBy> set = EnumSet.allOf(SortBy.class);
            
            if (sortBy != null) {
                switch (sortBy) {
                    case NAME:
                    case METHOD:
                        appendAttribute(sb, SortBy.NAME, values[SortBy.NAME.ordinal()][k], maxLength[SortBy.NAME.ordinal()]);
                        set.remove(SortBy.NAME);
                        
                        appendAttribute(sb, SortBy.METHOD, values[SortBy.METHOD.ordinal()][k], maxLength[SortBy.METHOD.ordinal()]);
                        set.remove(SortBy.METHOD);
                        break;
                    default:
                        appendAttribute(sb, sortBy, values[sortBy.ordinal()][k], maxLength[sortBy.ordinal()]);
                        set.remove(sortBy);
                        break;
                }
            }
            
            for (SortBy sort : SortBy.VALUES) {
                if (set.contains(sort)) {
                    appendAttribute(sb, sort, values[sort.ordinal()][k], maxLength[sort.ordinal()]);
                }
            }
            
            sb.append("/>");
            
            lines.add(sb.toString());
        }
        
        lines.add("</entries>");
        
        PrintStream ps = null;
        try {
            ps = new PrintStream("MethodStats-" + System.currentTimeMillis() + ".log");
            
            for (String line : lines) {
                ps.println(line);
            }
        } catch (Exception e) {
            log.warn("", e);
        } finally {
            IOUtils.closeQuietly(ps);
        }
    }
    
    /**
     * 添加XML属性
     * Append XML attribute
     */
    private static void appendAttribute(StringBuilder sb, SortBy sortBy, String value, int fillTo) {
        sb.append(sortBy.xmlAttributeName);
        sb.append("=");
        
        if (sortBy != SortBy.NAME && sortBy != SortBy.METHOD) {
            for (int i = value.length(); i < fillTo; i++) {
                sb.append(" ");
            }
        }
        
        sb.append("\"");
        sb.append(value);
        sb.append("\" ");
        
        if (sortBy == SortBy.NAME || sortBy == SortBy.METHOD) {
            for (int i = value.length(); i < fillTo; i++) {
                sb.append(" ");
            }
        }
    }
}
