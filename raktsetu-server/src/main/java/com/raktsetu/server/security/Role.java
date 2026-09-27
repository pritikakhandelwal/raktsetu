package com.raktsetu.server.security;

/** What an account may do. */
public enum Role {
    /** Network administrator: sees everything, plans and approves transfers, manages the demo date. */
    ADMIN,
    /** Staff at one blood centre: logs collections, reports stock, dispatches requests, receives transfers. */
    CENTRE_STAFF,
    /** Staff at one hospital: raises requests for that hospital and confirms receipt. */
    HOSPITAL_STAFF
}
