package eu.einfracentral.domain.aai;

import eu.einfracentral.domain.Identifiable;
import eu.einfracentral.domain.Provider;
import eu.einfracentral.domain.Service;

import javax.xml.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * Created by pgl on 30/6/2017.
 */
@XmlType(namespace = "http://einfracentral.eu", propOrder = {"id", "email", "password", "name", "surname", "joinDate",
        "memberships", "favourites", "iterationCount", "salt", "resetToken"})
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(namespace = "http://einfracentral.eu")
public class User implements Identifiable {
    @XmlElement
    private String id;
    @XmlElement
    private String email;
    @XmlElement
    private String password;
    @XmlElement
    private String name;
    @XmlElement
    private String surname;
    @XmlElement
    private String joinDate;
    @XmlElementWrapper(name = "memberships")
    @XmlElement(name = "membership")
    private Map<Provider, Grant> memberships;
    @XmlElementWrapper(name = "favourites")
    @XmlElement(name = "favourite")
    private List<Service> favourites;
    @XmlElement
    private int iterationCount;
    @XmlElement
    private byte[] salt;
    @XmlElement
    private String resetToken;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getJoinDate() {
        return joinDate;
    }

    public void setJoinDate(String joinDate) {
        this.joinDate = joinDate;
    }

    public Map<Provider, Grant> getMemberships() {
        return memberships;
    }

    public void setMemberships(Map<Provider, Grant> memberships) {
        this.memberships = memberships;
    }

    public List<Service> getFavourites() {
        return favourites;
    }

    public void setFavourites(List<Service> favourites) {
        this.favourites = favourites;
    }

    public int getIterationCount() {
        return iterationCount;
    }

    public void setIterationCount(int iterationCount) {
        this.iterationCount = iterationCount;
    }

    public byte[] getSalt() {
        return salt;
    }

    public void setSalt(byte[] salt) {
        this.salt = salt;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }
}
