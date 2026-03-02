package com.example.ratelimiter.controller;

import java.util.PriorityQueue;

public class Main {

    static class ListNode {
        int val;
        ListNode next;

        ListNode(int val) {
            this.val = val;
        }

        ListNode(int val, ListNode next) {
            this.val = val;
            this.next = next;
        }
    }

    public static void main(String[] args) {
        ListNode node1 = new ListNode(1);
        ListNode node2 = new ListNode(2);
        node1.next = node2;
        while (node1 != null) {
            System.out.println(node1.val);
            node1 = node1.next;
        }
    }
}