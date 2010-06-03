package edu.mit.simile.butterfly;

public class MountPoint {

    public static final MountPoint ROOT = new MountPoint("/");
    
    private String mountPoint;
    private String zone;

    /**
     * The mount point string is of the form "/path/ [zone]" or "/path/"
     */
    public MountPoint(String str) {
        str = str.trim();
        int separator = str.indexOf(' ');
        if (separator > 0) {
            mountPoint = str.substring(0, separator);
            str = str.substring(separator + 1);
            int length = str.length();
            if (str.indexOf('[') == 0 && str.lastIndexOf(']') == length - 1) {
                str = str.substring(1,length - 1).toLowerCase();
                zone = str;
            }
        } else {
            this.mountPoint = str;
        }

        if (!mountPoint.startsWith("/")) mountPoint = "/" + mountPoint;
        if (!mountPoint.endsWith("/")) mountPoint = mountPoint + "/";
    }
    
    public String getMountPoint() {
        return mountPoint;
    }

    public String getZone() {
        return zone;
    }

    @Override
    public String toString() {
        return mountPoint + " [" + ((zone == null) ? "*" : zone) + "]";
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mountPoint == null) ? 0 : mountPoint.hashCode());
        result = prime * result + ((zone == null) ? 0 : zone.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final MountPoint other = (MountPoint) obj;
        if (mountPoint == null) {
            if (other.mountPoint != null)
                return false;
        } else if (!mountPoint.equals(other.mountPoint))
            return false;
        if (zone == null) {
            if (other.zone != null)
                return false;
        } else if (!zone.equals(other.zone))
            return false;
        return true;
    }
    
}
