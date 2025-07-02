package io.github.vgnri.model;

public class ShareholderInfo {
    // enum定義
    public enum Gender {
        MALE("男"),
        FEMALE("女");
        
        private final String displayName;
        
        Gender(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public static Gender fromString(String text) {
            for (Gender gender : Gender.values()) {
                if (gender.displayName.equals(text)) {
                    return gender;
                }
            }
            throw new IllegalArgumentException("不正な性別: " + text);
        }
    }
    
    private int shareholderId;
    private String shareholderName;
    private Gender gender;
    private int age;
    private String occupation;
    private String city;
    private String district;
    
    // コンストラクタ
    public ShareholderInfo() {}
    
    public ShareholderInfo(int shareholderId, String shareholderName, Gender gender,
                          int age, String occupation, String city, String district) {
        this.shareholderId = shareholderId;
        this.shareholderName = shareholderName;
        this.gender = gender;
        this.age = age;
        this.occupation = occupation;
        this.city = city;
        this.district = district;
    }
    
    // CSVの行からShareholderInfoオブジェクトを作成するファクトリメソッド
    public static ShareholderInfo fromCsvLine(String csvLine) {
        String[] fields = csvLine.split(",");
        if (fields.length != 7) {
            throw new IllegalArgumentException("CSVの列数が正しくありません: " + csvLine);
        }
        
        try {
            int shareholderId = Integer.parseInt(fields[0].trim());
            String shareholderName = fields[1].trim();
            Gender gender = Gender.fromString(fields[2].trim());
            int age = Integer.parseInt(fields[3].trim());
            String occupation = fields[4].trim();
            String city = fields[5].trim();
            String district = fields[6].trim();
            
            return new ShareholderInfo(shareholderId, shareholderName, gender,
                                     age, occupation, city, district);
        } catch (Exception e) {
            throw new IllegalArgumentException("CSVの解析に失敗しました: " + csvLine, e);
        }
    }
    
    // Getter/Setter
    public int getShareholderId() {
        return shareholderId;
    }
    
    public void setShareholderId(int shareholderId) {
        this.shareholderId = shareholderId;
    }
    
    public String getShareholderName() {
        return shareholderName;
    }
    
    public void setShareholderName(String shareholderName) {
        this.shareholderName = shareholderName;
    }
    
    public Gender getGender() {
        return gender;
    }
    
    public void setGender(Gender gender) {
        this.gender = gender;
    }
    
    public int getAge() {
        return age;
    }
    
    public void setAge(int age) {
        this.age = age;
    }
    
    public String getOccupation() {
        return occupation;
    }
    
    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }
    
    public String getCity() {
        return city;
    }
    
    public void setCity(String city) {
        this.city = city;
    }
    
    public String getDistrict() {
        return district;
    }
    
    public void setDistrict(String district) {
        this.district = district;
    }
    
    @Override
    public String toString() {
        return "ShareholderInfo{" +
                "shareholderId=" + shareholderId +
                ", shareholderName='" + shareholderName + '\'' +
                ", gender=" + gender +
                ", age=" + age +
                ", occupation='" + occupation + '\'' +
                ", city='" + city + '\'' +
                ", district='" + district + '\'' +
                '}';
    }
}
