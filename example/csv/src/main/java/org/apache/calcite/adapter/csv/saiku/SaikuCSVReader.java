package org.apache.calcite.adapter.csv.saiku;

import java.io.IOException;
import java.io.Reader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.com.bytecode.opencsv.CSVReader;

public class SaikuCSVReader extends CSVReader {
  private int lineNumber;
  private int dateColumnIndex;
  private static final Pattern DATE_REGEX = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})");
  private static final DateFormat INPUT_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy");
  private static final DateFormat OUTPUT_DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd");
  private static final DateFormat MONTH_NAME_FORMAT = new SimpleDateFormat("MMMM");
  
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
    
    String[] newValues = new String[values.length + 6];
    
    System.arraycopy(values, 0, newValues, 0, values.length);
    
    if (this.lineNumber == 1) { // if we're reading the first line
      newValues[values.length + 0] = "DAY";
      newValues[values.length + 1] = "MONTH";
      newValues[values.length + 2] = "YEAR";
      newValues[values.length + 3] = "WEEK";
      newValues[values.length + 4] = "DATE_STRING";
      newValues[values.length + 5] = "SAIKU_GENERATED_ID";
      
      // Try to discover what's the date column
      this.dateColumnIndex = -1;
      for (int i = 0; i < values.length; i++) {
        if (values[i].equalsIgnoreCase("date")) {
          this.dateColumnIndex = i;
          break;
        }
      }
    } else {
      if (this.dateColumnIndex >= 0) {
        String dateString = values[this.dateColumnIndex];
        Matcher matcher = DATE_REGEX.matcher(dateString);
        
        if (matcher.matches()) {
          newValues[values.length + 1] = matcher.group(1); // Month
          newValues[values.length + 0] = matcher.group(2); // Day
          newValues[values.length + 2] = matcher.group(3); // Year
          newValues[values.length + 5] = "" + this.lineNumber; // Saiku Generated ID
          
          try {
            Calendar cal = Calendar.getInstance();
            
            cal.setTime(INPUT_DATE_FORMAT.parse(dateString));
            
            newValues[values.length + 1] = MONTH_NAME_FORMAT.format(cal.getTime());
            newValues[values.length + 3] = "" + cal.get(Calendar.WEEK_OF_YEAR);
            newValues[values.length + 4] = OUTPUT_DATE_FORMAT.format(cal.getTime());
          } catch (ParseException e) {
            e.printStackTrace();
          }
        } else {
          fillWithDefaultValues(newValues, values.length, this.lineNumber);
        }
      } else {
        fillWithDefaultValues(newValues, values.length, this.lineNumber);
      }
    }
    
    return newValues;
  }
  
  private void fillWithDefaultValues(String[] newValues, int offset, int id) {
    newValues[offset + 0] = "1";          // day
    newValues[offset + 1] = "";           // month
    newValues[offset + 2] = "1970";       // year
    newValues[offset + 3] = "1";          // week
    newValues[offset + 4] = "1970/01/01"; // date string
    newValues[offset + 5] = "" + id;      // Saiku Generated ID
  }
}
