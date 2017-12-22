package org.apache.calcite.adapter.csv.saiku;

import java.io.IOException;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import au.com.bytecode.opencsv.CSVReader;

public class SaikuCSVReader extends CSVReader {
  private int lineNumber;
  private int dateColumnIndex;
  private static final Pattern DATE_REGEX = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})");
  
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
    
    String[] newValues = new String[values.length + 3];
    
    System.arraycopy(values, 0, newValues, 0, values.length);
    
    if (this.lineNumber == 1) { // if we're reading the first line
      newValues[values.length + 0] = "DAY";
      newValues[values.length + 1] = "MONTH";
      newValues[values.length + 2] = "YEAR";
      
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
        } else {
          newValues[values.length + 0] = "1";
          newValues[values.length + 1] = "1";
          newValues[values.length + 2] = "1970";
        }
      } else {
        newValues[values.length + 0] = "1";
        newValues[values.length + 1] = "1";
        newValues[values.length + 2] = "1970";
      }
    }
    
    return newValues;
  }
}
