Feature: Customer booking API functionalities

  Scenario: Customer able to manage a booking
    Given A flight is available for booking
    When I search for the available flight
    Then The flight is returned
    When I book a standard seat for myself
    Then The booking is added and one seat is reserved
    When I view my bookings
    Then My new booking is listed
    When I cancel my booking
    Then The booking is cancelled and the seat is available again

  Scenario: Customer cannot book an occupied seat
    Given A flight is available for booking
    And Its first standard seat is already booked
    When Another customer books the same seat
    Then The duplicate booking is rejected without reserving another seat

  Scenario: Customer cannot book for another customer
    Given A flight is available for booking
    When I book a seat for another customer
    Then The booking is forbidden and no seat is reserved
