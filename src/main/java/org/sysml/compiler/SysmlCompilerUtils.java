package org.sysml.compiler;

import org.clafer.ast.AstClafer;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SysmlCompilerUtils {
    public static String getPropertyId(String name){
        Pattern pattern = Pattern.compile("(c[0-9]+_).*", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(name);
        boolean matchFound = matcher.find();
        //matcher.group()
        if (matchFound){
            String idxS = matcher.group(1);
            int idx = Integer.parseInt(idxS.substring(1, idxS.length()-1));
            return name.substring(matcher.group(1).length()) + "_" + String.valueOf(idx);
        } else {
            return name;
        }
    }

    public static String getPropertyAlias(String name){
        Pattern pattern = Pattern.compile("(c[0-9]+_).*", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(name);
        boolean matchFound = matcher.find();
        if (matchFound){
            return name.substring(matcher.group(1).length());
        } else {
            return name;
        }
    }

    public static String[] getSuperClafers(AstClafer model){
        Object spr = model.getSuperClafer();
        if (spr == null){
            return new String[0];
        } else {
            String[] rem = getSuperClafers((AstClafer) spr);
            String[] sprs = Arrays.copyOf(rem, rem.length + 1);
            sprs[0] = SysmlCompilerUtils.getPropertyId(((AstClafer) spr).getName());
            System.arraycopy(rem, 0, sprs, 1, rem.length);
            return sprs;
        }
    }
}
