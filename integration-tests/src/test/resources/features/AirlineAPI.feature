Feature: FAA and airline administration API functionalities

  Scenario: FAA admin creates an airline and its administrator
    Given The FAA admin has details for a new airline
    When The FAA admin creates the airline
    Then The new airline is listed
    When The FAA admin adds an administrator to the new airline
    Then The new administrator belongs to that airline

  Scenario: Airline admin schedules a flight and assigns its crew
    Given An airline admin has an active airplane available
    When The airline admin schedules a flight
    Then The flight is waiting for crew
    When The airline admin assigns both pilots and a flight attendant
    Then The three crew assignments are listed
    When The airline admin activates the flight
    Then The flight is active and available for booking
