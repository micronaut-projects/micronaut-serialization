package io.micronaut.serde.toml.fixture;

import io.micronaut.serde.annotation.Serdeable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Serdeable
public class MediaItem {
    private MediaContent content;
    private List<Image> images;

    public MediaContent getContent() {
        return content;
    }

    public void setContent(MediaContent content) {
        this.content = content;
    }

    public List<Image> getImages() {
        return images;
    }

    public void setImages(List<Image> images) {
        this.images = images;
    }

    public void addPhoto(Image image) {
        if (images == null) {
            images = new ArrayList<>();
        }
        images.add(image);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MediaItem mediaItem)) {
            return false;
        }
        return Objects.equals(content, mediaItem.content) && Objects.equals(images, mediaItem.images);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, images);
    }

    @Serdeable
    public static class MediaContent {
        public enum Player {
            JAVA,
            FLASH
        }

        private Player player;
        private String uri;
        private String title;
        private int width;
        private int height;
        private String format;
        private long duration;
        private long size;
        private int bitrate;
        private List<String> persons;
        private String copyright;

        public Player getPlayer() {
            return player;
        }

        public void setPlayer(Player player) {
            this.player = player;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public long getDuration() {
            return duration;
        }

        public void setDuration(long duration) {
            this.duration = duration;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public int getBitrate() {
            return bitrate;
        }

        public void setBitrate(int bitrate) {
            this.bitrate = bitrate;
        }

        public List<String> getPersons() {
            return persons;
        }

        public void setPersons(List<String> persons) {
            this.persons = persons;
        }

        public String getCopyright() {
            return copyright;
        }

        public void setCopyright(String copyright) {
            this.copyright = copyright;
        }

        public void addPerson(String person) {
            if (persons == null) {
                persons = new ArrayList<>();
            }
            persons.add(person);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof MediaContent that)) {
                return false;
            }
            return width == that.width
                && height == that.height
                && duration == that.duration
                && size == that.size
                && bitrate == that.bitrate
                && player == that.player
                && Objects.equals(uri, that.uri)
                && Objects.equals(title, that.title)
                && Objects.equals(format, that.format)
                && Objects.equals(persons, that.persons)
                && Objects.equals(copyright, that.copyright);
        }

        @Override
        public int hashCode() {
            return Objects.hash(player, uri, title, width, height, format, duration, size, bitrate, persons, copyright);
        }
    }

    @Serdeable
    public static class Image {
        public enum Size {
            SMALL,
            LARGE
        }

        private String uri;
        private String title;
        private int width;
        private int height;
        private Size size;

        public Image() {
        }

        public Image(String uri, String title, int width, int height, Size size) {
            this.uri = uri;
            this.title = title;
            this.width = width;
            this.height = height;
            this.size = size;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public Size getSize() {
            return size;
        }

        public void setSize(Size size) {
            this.size = size;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Image image)) {
                return false;
            }
            return width == image.width
                && height == image.height
                && Objects.equals(uri, image.uri)
                && Objects.equals(title, image.title)
                && size == image.size;
        }

        @Override
        public int hashCode() {
            return Objects.hash(uri, title, width, height, size);
        }
    }
}
