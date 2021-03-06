/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.adapter.csv;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.apache.calcite.adapter.csv.saiku.SaikuCSVReader;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.util.Pair;
import org.apache.commons.lang3.time.FastDateFormat;

import au.com.bytecode.opencsv.CSVReader;


/** Enumerator that reads from a CSV file.
 *
 * @param <E> Row type
 */
class CsvEnumerator<E> implements Enumerator<E> {
  private final SaikuCSVReader reader;
  private final String[] filterValues;
  private final RowConverter<E> rowConverter;
  private E current;
  private static File file;

  private static final FastDateFormat TIME_FORMAT_DATE;
  private static final FastDateFormat TIME_FORMAT_TIME;
  private static final FastDateFormat TIME_FORMAT_TIMESTAMP;
  
  private static final Pattern DATE_REGEX;
  
  static {
    TimeZone gmt = TimeZone.getTimeZone("GMT");
    TIME_FORMAT_DATE = FastDateFormat.getInstance("MM/dd/yyyy", gmt);
    TIME_FORMAT_TIME = FastDateFormat.getInstance("HH:mm:ss", gmt);
    TIME_FORMAT_TIMESTAMP = FastDateFormat.getInstance("MM/dd/yyyy HH:mm", gmt);
    
    DATE_REGEX = Pattern.compile("\\d{1,2}/\\d{1,2}/\\d{4}");
  }

  public CsvEnumerator(InputStream stream, List<CsvFieldType> fieldTypes) {
    this(stream, fieldTypes, identityList(fieldTypes.size()));
  }
  
  public CsvEnumerator(File file, List<CsvFieldType> fieldTypes) {
    this(file, fieldTypes, identityList(fieldTypes.size()));
  }

  public CsvEnumerator(InputStream stream, List<CsvFieldType> fieldTypes, int[] fields) {
    this(stream, null, (RowConverter<E>) converter(fieldTypes, fields));
  }

  public CsvEnumerator(File file, List<CsvFieldType> fieldTypes, int[] fields) {
    //noinspection unchecked
    this(file, null, (RowConverter<E>) converter(fieldTypes, fields));
  }

  public CsvEnumerator(File file, String[] filterValues,
      RowConverter<E> rowConverter) {
    this.rowConverter = rowConverter;
    this.filterValues = filterValues;
    try {
      this.reader = openCsv(file);
      this.reader.readNext(); // skip header row
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  public CsvEnumerator(InputStream stream, String[] filterValues,
      RowConverter<E> rowConverter) {
    this.rowConverter = rowConverter;
    this.filterValues = filterValues;
    try {
      this.reader = openCsv(stream);
      this.reader.readNext(); // skip header row
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static RowConverter<?> converter(List<CsvFieldType> fieldTypes,
      int[] fields) {
    if (fields.length == 1) {
      final int field = fields[0];
      return new SingleColumnRowConverter(fieldTypes.get(field), field);
    } else {
      return new ArrayRowConverter(fieldTypes, fields);
    }
  }
  
  /** Deduces the names and types of a table's columns by reading the first line
   * of a CSV file. */
  static RelDataType deduceRowType(JavaTypeFactory typeFactory, File file,
      List<CsvFieldType> fieldTypes) {
    try {
      return deduceRowType(typeFactory, new FileInputStream(file), fieldTypes);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  /** Deduces the names and types of a table's columns by reading the first line
   * of a CSV file. */
  static RelDataType deduceRowType(JavaTypeFactory typeFactory, InputStream stream,
      List<CsvFieldType> fieldTypes) {
    final List<RelDataType> types = new ArrayList<>();
    final List<String> names = new ArrayList<>();
    CSVReader reader = null;
    try {
      reader = openCsv(stream);
      final String[] strings = reader.readNext();
      int col = 0;
      for (String string : strings) {
        final String name;
        CsvFieldType fieldType = null;
        final int colon = string.indexOf(':');
        if (colon >= 0) {
          name = string.substring(0, colon);
          String typeString = string.substring(colon + 1);
          fieldType = CsvFieldType.of(typeString);
          if(fieldType == null){
            String[] nextline = reader.readNext();
            if(nextline!=null) {
              String field = nextline[col];
              if(field!=null){
                fieldType = CsvFieldType.of(convertField(field).toString());
              }
            }
          }
          if (fieldType == null) {
            System.out.println("WARNING: Found unknown type: "
              + typeString + " in file "
              + " for column: " + name
              + ". Will assume the type of column is string");
          }
        } else {
          String type = null;
          CSVReader reader2 = openCsv(stream);
          reader2.readNext();
          for(int i = 0; i<20; i++) {
            String[] nextline = reader2.readNext();
            if(nextline == null){
              break;
            }
            else{
              String field = nextline[col];
              if (field != null) {
                String object = convertField(field);
                if(type == null){
                  type = object;
                }
                if(type!=null & !type.equals(object)){
                  type="string";
                }

              }
            }
          }
          fieldType = CsvFieldType.of(type);
          name = string;
          col++;
          //fieldType = null;
        }
        final RelDataType type;
        if (fieldType == null) {
          type = typeFactory.createJavaType(String.class);
        } else {
          type = fieldType.toType(typeFactory);
        }
        names.add(name);
        types.add(type);
        if (fieldTypes != null) {
          fieldTypes.add(fieldType);
        }
      }
    } catch (IOException e) {
      // ignore
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
          // ignore
        }
      }
    }
    
    if (names.isEmpty()) {
      names.add("line");
      types.add(typeFactory.createJavaType(String.class));
    }
    
    return typeFactory.createStructType(Pair.zip(names, types));
  }

  private static String convertField(String f){
    String o;

    o = isNumeric(f);

    if (o == null) {
      o = isInteger(f);
    }
    
    if (o == null && DATE_REGEX.matcher(f).matches()) {
      o = "date";
    }
    
    if (o == null) {
      o = "string";
    }

    return o;
  }

  private static String isNumeric(String str) throws NumberFormatException
  {

    try {
      Double.parseDouble(str);
      return "double";
    } catch (NumberFormatException e) {
      return null;
    }

  }

  public static String isInteger(String s) throws NumberFormatException{

    try {
      Integer.parseInt(s);
      return "int";
    }
    catch(NumberFormatException e){
      return null;
    }

  }

  private static SaikuCSVReader openCsv(File file) throws IOException {
    final Reader fileReader;
    if (file.getName().endsWith(".gz")) {
      final GZIPInputStream inputStream =
          new GZIPInputStream(new FileInputStream(file));
      fileReader = new InputStreamReader(inputStream);
    } else {
      fileReader = new FileReader(file);
    }
    return new SaikuCSVReader(fileReader);
  }
  
  private static SaikuCSVReader openCsv(InputStream stream) throws IOException {
    if (CsvEnumerator.file != null) {
      return new SaikuCSVReader(new FileReader(file));
    } else {
      stream.reset();
      return new SaikuCSVReader(new InputStreamReader(stream));
    }
  }

  public E current() {
    return current;
  }

  public boolean moveNext() {
    try {
    outer:
      for (;;) {
        final String[] strings = reader.readNext();
        if (strings == null) {
          current = null;
          reader.close();
          return false;
        }
        if (filterValues != null) {
          for (int i = 0; i < strings.length; i++) {
            String filterValue = filterValues[i];
            if (filterValue != null) {
              if (!filterValue.equals(strings[i])) {
                continue outer;
              }
            }
          }
        }
        current = rowConverter.convertRow(strings);
        return true;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void reset() {
    throw new UnsupportedOperationException();
  }

  public void close() {
    try {
      reader.close();
    } catch (IOException e) {
      throw new RuntimeException("Error closing CSV reader", e);
    }
  }

  /** Returns an array of integers {0, ..., n - 1}. */
  static int[] identityList(int n) {
    int[] integers = new int[n];
    for (int i = 0; i < n; i++) {
      integers[i] = i;
    }
    return integers;
  }

  /** Row converter. */
  abstract static class RowConverter<E> {
    abstract E convertRow(String[] rows);

    protected Object convert(CsvFieldType fieldType, String string) {
      if (fieldType == null) {
        return string;
      }
      switch (fieldType) {
      case BOOLEAN:
        if (string.length() == 0) {
          return null;
        }
        return Boolean.parseBoolean(string);
      case BYTE:
        if (string.length() == 0) {
          return null;
        }
        return Byte.parseByte(string);
      case SHORT:
        if (string.length() == 0) {
          return null;
        }
        return Short.parseShort(string);
      case INT:
        if (string.length() == 0) {
          return null;
        }
        return Integer.parseInt(string);
      case LONG:
        if (string.length() == 0) {
          return null;
        }
        return Long.parseLong(string);
      case FLOAT:
        if (string.length() == 0) {
          return null;
        }
        return Float.parseFloat(string);
      case DOUBLE:
        if (string.length() == 0) {
          return null;
        }
        return Double.parseDouble(string);
      case DATE:
        if (string.length() == 0) {
          return null;
        }
        try {
          Date date = TIME_FORMAT_DATE.parse(string);
          return new java.sql.Date(date.getTime());
        } catch (ParseException e) {
          return null;
        }
      case TIME:
        if (string.length() == 0) {
          return null;
        }
        try {
          Date date = TIME_FORMAT_TIME.parse(string);
          return new java.sql.Time(date.getTime());
        } catch (ParseException e) {
          return null;
        }
      case TIMESTAMP:
        if (string.length() == 0) {
          return null;
        }
        try {
          Date date = TIME_FORMAT_TIMESTAMP.parse(string);
          return new java.sql.Timestamp(date.getTime());
        } catch (ParseException e) {
          return null;
        }
      case STRING:
      default:
        return string;
      }
    }
  }

  /** Array row converter. */
  static class ArrayRowConverter extends RowConverter<Object[]> {
    private final CsvFieldType[] fieldTypes;
    private final int[] fields;

    ArrayRowConverter(List<CsvFieldType> fieldTypes, int[] fields) {
      this.fieldTypes = fieldTypes.toArray(new CsvFieldType[fieldTypes.size()]);
      this.fields = fields;
    }

    public Object[] convertRow(String[] strings) {
      final Object[] objects = new Object[fields.length];
      for (int i = 0; i < fields.length; i++) {
        int field = fields[i];
        objects[i] = convert(fieldTypes[field], strings[field]);
      }
      return objects;
    }
  }

  /** Single column row converter. */
  private static class SingleColumnRowConverter extends RowConverter {
    private final CsvFieldType fieldType;
    private final int fieldIndex;

    private SingleColumnRowConverter(CsvFieldType fieldType, int fieldIndex) {
      this.fieldType = fieldType;
      this.fieldIndex = fieldIndex;
    }

    public Object convertRow(String[] strings) {
      return convert(fieldType, strings[fieldIndex]);
    }
  }
  
  public static File getFile() {
    return file;
  }
  
  public static void setFile(File f) {
    file = f;
  }
}

// End CsvEnumerator.java
