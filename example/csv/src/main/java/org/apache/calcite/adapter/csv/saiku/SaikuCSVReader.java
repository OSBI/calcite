package org.apache.calcite.adapter.csv.saiku;

import java.io.IOException;
import java.io.Reader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.com.bytecode.opencsv.CSVReader;

public class SaikuCSVReader extends CSVReader {
  private static final Pattern DATE_REGEX[] = new Pattern[] {
      Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})"),
      Pattern.compile("(\\d{4})/(\\d{1,2})/(\\d{1,2})"),
      Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})"),
      Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})")
  };
  
  private static final Pattern TIME_REGEX = Pattern.compile("(\\d{1,2}):(\\d{1,2})");
  
  // For each one of the input regular expressions, is associated an array of possible date formats 
  private static final DateFormat INPUT_DATE_FORMAT[][] = new DateFormat[][] {
      new DateFormat[] {new SimpleDateFormat("MM/dd/yyyy"), new SimpleDateFormat("dd/MM/yyyy")},
      new DateFormat[] {new SimpleDateFormat("yyyy/MM/dd")},
      new DateFormat[] {new SimpleDateFormat("yyyy-MM-dd")},
      new DateFormat[] {new SimpleDateFormat("MM.dd.yyyy"), new SimpleDateFormat("dd.MM.yyyy")}
  };
  private static final DateFormat OUTPUT_DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd");
  private static final DateFormat INPUT_TIME_FORMAT = new SimpleDateFormat("HH:mm");
  private static final DateFormat OUTPUT_DATE_TIME_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm");
  
  private static final DateFormat DEFAULT_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy");
  
  private int lineNumber;
  private int dateColumnIndex;
  private int timeColumnIndex;
  
  static {
    for (int i = 0; i < INPUT_DATE_FORMAT.length; i++) {
      for (int j = 0; j < INPUT_DATE_FORMAT[i].length; j++) {
        INPUT_DATE_FORMAT[i][j].setLenient(false);
      }
    }
  }
  
  public SaikuCSVReader(Reader reader) {
    super(reader);
    this.lineNumber = 0;
  }

  @Override
  public String[] readNext() throws IOException {
    this.lineNumber++;
    
    String[] values = super.readNext();
    
    if (values == null) {
      return values;
    }
    
    String[] newValues = new String[values.length + 2];
    
    System.arraycopy(values, 0, newValues, 0, values.length);
    
    if (this.lineNumber == 1) { // if we're reading the first line
      newValues[values.length + 0] = "DATE_STRING";
      newValues[values.length + 1] = "DATE_TIME_STRING";
      
      // Try to discover what's the date column
      this.dateColumnIndex = -1;
      for (int i = 0; i < values.length; i++) {
        if (values[i].equalsIgnoreCase("date")) {
          this.dateColumnIndex = i;
          break;
        }
      }
      
      // Try to discover what's the time column
      this.timeColumnIndex = -1;
      for (int i = 0; i < values.length; i++) {
        if (values[i].equalsIgnoreCase("time")) {
          this.timeColumnIndex = i;
          break;
        }
      }
    } else {
      Calendar cal = createDateCalendar(values);
      newValues[values.length + 0] = OUTPUT_DATE_FORMAT.format(cal.getTime());
      newValues[values.length + 1] = OUTPUT_DATE_TIME_FORMAT.format(createDateTimeCalendar(values, cal).getTime());
      
      if (this.dateColumnIndex >= 0) {
        newValues[this.dateColumnIndex] = DEFAULT_DATE_FORMAT.format(cal.getTime());
      }
    }
    
    return newValues;
  }
  
  private Calendar createDateTimeCalendar(String[] values, Calendar dateCalendar) {
    // If there's a time column
    if (this.timeColumnIndex >= 0) {
      String timeString = values[this.timeColumnIndex];
      Matcher timeMatcher = TIME_REGEX.matcher(timeString);
      
      if (timeMatcher.matches()) {
        try {
          Date timeObj = INPUT_TIME_FORMAT.parse(timeString);
          Calendar timeCal = Calendar.getInstance();
          timeCal.setTime(timeObj);
          dateCalendar.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
          dateCalendar.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
        } catch (ParseException e) {
          e.printStackTrace();
        }
      }
    }
    
    return dateCalendar;
  }
  
  private Calendar createDateCalendar(String[] values) {
    Calendar cal = Calendar.getInstance();
    
    // If there's a date column
    if (this.dateColumnIndex >= 0) {
      String dateString = values[this.dateColumnIndex];
      
      for (int i = 0; i < DATE_REGEX.length; i++) {
        Matcher matcher = DATE_REGEX[i].matcher(dateString);
        
        if (matcher.matches()) {
          for (int j = 0; j < INPUT_DATE_FORMAT[i].length; j++) {
            try {
              cal.setTime(INPUT_DATE_FORMAT[i][j].parse(dateString));
              break;
            } catch (ParseException e) {
              // Check the next date format
            }
          }
          
          break;
        }
      }
    }
    
    return cal;
  }
}
