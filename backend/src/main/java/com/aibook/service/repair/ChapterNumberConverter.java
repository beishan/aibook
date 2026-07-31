package com.aibook.service.repair;

/**
 * 中文数字与阿拉伯数字互转工具
 */
public final class ChapterNumberConverter {

    private ChapterNumberConverter() {}

    private static final String[] CHINESE_DIGITS = {
        "零", "一", "二", "三", "四", "五", "六", "七", "八", "九"
    };

    private static final String[] CHINESE_UNITS = {
        "", "十", "百", "千", "万"
    };

    private static final String[] FORMAL_DIGITS = {
        "零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"
    };

    private static final String[] FORMAL_UNITS = {
        "", "拾", "佰", "仟", "万"
    };

    /**
     * 将中文数字或阿拉伯数字字符串转换为整数
     * <p>
     * 支持的输入：
     * - "一" → 1
     * - "十" → 10
     * - "十一" → 11
     * - "二十五" → 25
     * - "一百零三" → 103
     * - "一千零二" → 1002
     * - "两百" → 200
     * - "001" → 1
     * - "123" → 123
     */
    public static int chineseToNumber(String text) {
        if (text == null || text.isEmpty()) return 0;

        text = text.trim();

        // 纯数字
        if (text.matches("\\d+")) {
            return Integer.parseInt(text);
        }

        // 处理 "〇" 作为零
        text = text.replace("〇", "零");

        // 将正式大写数字转为普通数字
        for (int i = 0; i < FORMAL_DIGITS.length; i++) {
            text = text.replace(FORMAL_DIGITS[i], CHINESE_DIGITS[i]);
        }
        for (int i = 0; i < FORMAL_UNITS.length; i++) {
            text = text.replace(FORMAL_UNITS[i], CHINESE_UNITS[i]);
        }

        // 处理 "两" → "二"
        text = text.replace("两", "二");

        // 逐字编号模式：如 "一二" → 12
        if (isSequentialDigits(text)) {
            return parseSequentialDigits(text);
        }

        // 中文数值表达式解析
        return parseChineseNumber(text);
    }

    /**
     * 将整数转换为中文数字
     */
    public static String numberToChinese(int number) {
        if (number == 0) return "零";
        if (number < 0) return "负" + numberToChinese(-number);

        StringBuilder sb = new StringBuilder();
        if (number >= 10000) {
            int wan = number / 10000;
            int remainder = number % 10000;
            sb.append(convertUnder10000(wan)).append("万");
            if (remainder > 0) {
                if (remainder < 1000) sb.append("零");
                sb.append(convertUnder10000(remainder));
            }
        } else {
            sb.append(convertUnder10000(number));
        }
        return sb.toString();
    }

    // ==================== 内部方法 ====================

    private static boolean isSequentialDigits(String text) {
        for (char c : text.toCharArray()) {
            boolean found = false;
            for (String digit : CHINESE_DIGITS) {
                if (digit.charAt(0) == c) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static int parseSequentialDigits(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            for (int i = 0; i < CHINESE_DIGITS.length; i++) {
                if (CHINESE_DIGITS[i].charAt(0) == c) {
                    sb.append(i);
                    break;
                }
            }
        }
        try {
            return Integer.parseInt(sb.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseChineseNumber(String text) {
        int total = 0;
        int current = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int digitValue = getDigitValue(c);
            int unitValue = 0;

            if (digitValue >= 0) {
                current = digitValue;
                // 检查后面是否有单位
                if (i + 1 < text.length()) {
                    char next = text.charAt(i + 1);
                    unitValue = getUnitValue(next);
                    if (unitValue > 0) {
                        if (unitValue == 10000) {
                            total = (total + current) * unitValue;
                            current = 0;
                        } else {
                            current *= unitValue;
                        }
                        i++; // 跳过单位字符
                    }
                }
                if (unitValue == 0) {
                    total += current;
                    current = 0;
                }
            }
        }
        total += current;
        return total;
    }

    private static int getDigitValue(char c) {
        for (int i = 0; i < CHINESE_DIGITS.length; i++) {
            if (CHINESE_DIGITS[i].charAt(0) == c) return i;
        }
        return -1;
    }

    private static int getUnitValue(char c) {
        if (c == '十') return 10;
        if (c == '百') return 100;
        if (c == '千') return 1000;
        if (c == '万') return 10000;
        return 0;
    }

    private static String convertUnder10000(int number) {
        if (number == 0) return "";
        StringBuilder sb = new StringBuilder();
        int thousand = number / 1000;
        int hundred = (number % 1000) / 100;
        int ten = (number % 100) / 10;
        int unit = number % 10;

        if (thousand > 0) {
            sb.append(CHINESE_DIGITS[thousand]).append("千");
        }
        if (hundred > 0) {
            if (thousand == 0 && sb.length() > 0) sb.append("零");
            sb.append(CHINESE_DIGITS[hundred]).append("百");
        }
        if (ten > 0) {
            if (hundred == 0 && (thousand > 0)) sb.append("零");
            if (ten == 1 && sb.length() == 0) {
                sb.append("十");
            } else {
                sb.append(CHINESE_DIGITS[ten]).append("十");
            }
        }
        if (unit > 0) {
            if (ten == 0 && (hundred > 0 || thousand > 0)) sb.append("零");
            sb.append(CHINESE_DIGITS[unit]);
        }
        return sb.toString();
    }
}
