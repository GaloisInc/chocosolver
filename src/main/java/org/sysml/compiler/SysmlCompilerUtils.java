package org.sysml.compiler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SysmlCompilerUtils {
    public static String getPropertyId(String name){
        Pattern pattern = Pattern.compile("(c[0-9]+_).*", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(name);
        boolean matchFound = matcher.find();
        //matcher.group()
        if (matchFound){
            return name.substring(matcher.group(1).length());
        } else {
            return name;
        }
    }

}
