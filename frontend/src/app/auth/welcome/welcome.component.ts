import { Component } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-welcome',
  standalone: true,
  templateUrl: './welcome.component.html',
  styleUrls: ['./welcome.component.scss']
})
export class WelcomeComponent {
  constructor(private router: Router) {}

  goToSignin(): void {
    this.router.navigate(['/signin']);
  }

  goToSignup(): void {
    this.router.navigate(['/signup']);
  }

  goToClient(): void {
    this.router.navigate(['/avis-client']);
  }
}
