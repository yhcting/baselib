package free.yhc.baselib.adapter.javase;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import free.yhc.baselib.Logger;
import free.yhc.baselib.adapter.LoggerAdapter;

public class JLoggerAdapter implements LoggerAdapter {
    private final PrintWriter mPr;
    private static DateFormat sLogTimeDateFormat
            = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                                             DateFormat.MEDIUM,
                                             Locale.ENGLISH);

    public JLoggerAdapter(File logfile) throws FileNotFoundException {
        if (null == logfile)
            mPr = new PrintWriter(System.out);
        else
            mPr = new PrintWriter(new OutputStreamWriter(new FileOutputStream(logfile)));
    }

    @Override
    public void
    write(@NotNull Logger.LogLv lv, @NotNull String m) {
        String pref;
        switch (lv) {
        case V: pref = "[V]"; break;
        case D: pref = "[D]"; break;
        case I: pref = "[I]"; break;
        case W: pref = "[W]"; break;
        case E: pref = "[E]"; break;
        case F: pref = "[F]"; break;
        default:
            throw new AssertionError();
        }
        long timems = System.currentTimeMillis();
        mPr.printf(
                "%s<%s:%03d> [%s] %s\n",
                pref,
                sLogTimeDateFormat.format(new Date(timems)),
                timems % 1000,
                lv.name(),
                m);
        mPr.flush();
    }
}
